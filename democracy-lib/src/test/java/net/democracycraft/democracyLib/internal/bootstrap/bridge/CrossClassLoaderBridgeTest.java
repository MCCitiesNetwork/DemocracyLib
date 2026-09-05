package net.democracycraft.democracyLib.internal.bootstrap.bridge;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the cross-plugin ClassCastException in leader/follower resource sharing.
 * <p>
 * DemocracyLib is shaded unrelocated into every consuming plugin, so the same class exists once
 * per plugin classloader. This test reproduces that topology with two {@link ShadedCopyClassLoader}s:
 * the "leader" world owns the real services, the "follower" world talks to them through the
 * bridge proxies — and must never receive an object it cannot hold. A single-classloader test
 * cannot catch this class of bug.
 */
class CrossClassLoaderBridgeTest {

    private static final String PKG = "net.democracycraft.democracyLib.";

    private ShadedCopyClassLoader leaderWorld;
    private ShadedCopyClassLoader followerWorld;
    private ExecutorService executor;
    private Plugin plugin;

    private Object leaderCache;
    private Object leaderMojangService;

    @BeforeEach
    void setUp() throws Exception {
        leaderWorld = new ShadedCopyClassLoader(getClass().getClassLoader());
        followerWorld = new ShadedCopyClassLoader(getClass().getClassLoader());
        executor = Executors.newCachedThreadPool();
        plugin = mockPlugin("CrossLoaderTestPlugin");

        leaderCache = leaderWorld.loadClass(PKG + "internal.cache.MojangServiceDemocracyCacheImpl")
                .getConstructor(ExecutorService.class)
                .newInstance(executor);
        leaderMojangService = leaderWorld.loadClass(PKG + "internal.service.mojang.MojangServiceImpl")
                .getConstructors()[0]
                .newInstance(plugin, leaderCache);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void followerCanHoldEverythingTheMojangProxyReturns() throws Exception {
        Object mojangProxy = followerMojangProxy();
        assertTrue(followerWorld.loadClass(PKG + "api.service.mojang.MojangService").isInstance(mojangProxy));

        // The reported incident: getCache() used to hand the follower the leader's
        // MojangServiceDemocracyCacheImpl, which cannot be cast to the follower's DemocracyCache.
        Object cache = call(mojangProxy, "getCache");
        assertNotNull(cache);
        assertTrue(followerWorld.loadClass(PKG + "api.cache.DemocracyCache").isInstance(cache),
                "follower must be able to hold getCache() as its own DemocracyCache");
        assertTrue(followerWorld.loadClass(PKG + "api.cache.MojangServiceDemocracyCache").isInstance(cache),
                "downcast to the concrete cache interface must keep working");

        // JDK-typed members survive because the JDK is a shared parent loader.
        assertSame(executor, call(mojangProxy, "getExecutorService"));
        assertSame(plugin, call(mojangProxy, "getBoundPlugin"));
    }

    @Test
    void nameAndUuidMapsAreTheLeadersLiveMaps() throws Exception {
        Object followerCache = call(followerMojangProxy(), "getCache");

        Map<?, ?> followerNameMap = (Map<?, ?>) call(followerCache, "getUniqueIdentifierToNameMap");
        Map<?, ?> leaderNameMap = (Map<?, ?>) call(leaderCache, "getUniqueIdentifierToNameMap");

        // Purely JDK-typed maps cross as-is: state is genuinely shared, not copied.
        assertSame(leaderNameMap, followerNameMap);
    }

    @Test
    void skinMapTranslatesValuesInBothDirections() throws Exception {
        UUID primed = UUID.randomUUID();
        UUID writtenByFollower = UUID.randomUUID();

        Map<Object, Object> leaderSkins = leaderSkinMap();
        leaderSkins.put(primed, skinOf(leaderWorld, "leader-value", "leader-signature"));

        Object followerCache = call(followerMojangProxy(), "getCache");
        @SuppressWarnings("unchecked")
        Map<Object, Object> followerSkins = (Map<Object, Object>) call(followerCache, "getUniqueIdentifierToSkinMap");

        // Read: the follower sees its own SkinDto, re-created from the leader's data.
        Object seen = followerSkins.get(primed);
        assertNotNull(seen);
        assertTrue(followerWorld.loadClass(PKG + "api.data.SkinDto").isInstance(seen));
        assertEquals("leader-value", call(seen, "value"));

        // Write: the follower primes the shared cache; the leader must be able to hold the entry.
        followerSkins.put(writtenByFollower, skinOf(followerWorld, "follower-value", "follower-signature"));
        Object stored = leaderSkins.get(writtenByFollower);
        assertNotNull(stored);
        assertTrue(leaderWorld.loadClass(PKG + "api.data.SkinDto").isInstance(stored));
        assertEquals("follower-value", call(stored, "value"));
    }

    @Test
    void getSkinFutureDeliversAFollowerLocalDto() throws Exception {
        UUID primed = UUID.randomUUID();
        leaderSkinMap().put(primed, skinOf(leaderWorld, "cached-value", "cached-signature"));

        Object mojangProxy = followerMojangProxy();
        CompletableFuture<?> future = (CompletableFuture<?>) call(mojangProxy, "getSkin", primed);
        Object dto = future.get(10, TimeUnit.SECONDS);

        assertNotNull(dto);
        assertTrue(followerWorld.loadClass(PKG + "api.data.SkinDto").isInstance(dto));
        assertEquals("cached-value", call(dto, "value"));
    }

    @Test
    void gitHubConfigurationCrossesInBothDirections() throws Exception {
        Object leaderConfig = leaderWorld.loadClass(PKG + "internal.config.GitHubGistConfigurationImpl")
                .getConstructor().newInstance();
        Object leaderGistService = leaderWorld.loadClass(PKG + "internal.service.github.GitHubGistServiceImpl")
                .getConstructors()[0]
                .newInstance(plugin, executor, leaderConfig, HttpClient.newHttpClient());

        Object gistProxy = followerWorld.loadClass(PKG + "internal.bootstrap.proxy.DemocracyServiceProxies")
                .getMethod("githubProxy", Object.class)
                .invoke(null, leaderGistService);

        // Leader -> follower: the follower holds the leader's config as its own interface.
        Object configSeen = call(gistProxy, "getConfiguration");
        assertNotNull(configSeen);
        assertTrue(followerWorld.loadClass(PKG + "api.config.github.GitHubGistConfiguration").isInstance(configSeen));
        assertEquals("https://api.github.com", call(configSeen, "getApiBaseUrl"));

        // Follower -> leader: a follower-owned config object must be acceptable to the leader.
        Object followerConfig = followerWorld.loadClass(PKG + "internal.config.GitHubGistConfigurationImpl")
                .getConstructor().newInstance();
        call(gistProxy, "setConfiguration", followerConfig);

        Object configOnLeader = call(leaderGistService, "getConfiguration");
        assertNotNull(configOnLeader);
        assertTrue(leaderWorld.loadClass(PKG + "api.config.github.GitHubGistConfiguration").isInstance(configOnLeader),
                "leader must be able to hold a follower-provided configuration");
        assertEquals("https://api.github.com", call(configOnLeader, "getApiBaseUrl"));
    }

    @Test
    void followerCanRegisterAndRecoverItsOwnService() throws Exception {
        Object leaderManager = leaderWorld.loadClass(PKG + "internal.service.engine.DemocracyServiceManagerImpl")
                .getConstructor().newInstance();
        Object followerBridge = followerWorld.loadClass(PKG + "internal.bootstrap.DemocracyBridgeServiceManager")
                .getConstructor(Object.class).newInstance(leaderManager);

        Object followerService = followerWorld.loadClass(PKG + "internal.bootstrap.bridge.fixture.FixtureFollowerService")
                .getConstructor(Plugin.class).newInstance(plugin);

        // Used to fail: the leader's MethodHandle cast can never accept a follower's service object.
        call(followerBridge, "registerService", followerService);

        List<?> allOnLeader = (List<?>) call(leaderManager, "getAllServices");
        assertEquals(1, allOnLeader.size());
        Object registered = allOnLeader.get(0);
        assertTrue(leaderWorld.loadClass(PKG + "api.service.engine.PluginBoundDemocracyService").isInstance(registered),
                "leader must hold the follower's service as its own interface");
        assertEquals("FixtureFollowerService", call(registered, "getServiceName"));
        assertSame(plugin, call(registered, "getBoundPlugin"));

        // Round trip: looking the service up from the follower unwraps back to the original object.
        Object recovered = call(followerBridge, "getServiceForPlugin",
                plugin, followerWorld.loadClass(PKG + "api.service.engine.PluginBoundDemocracyService"));
        assertSame(followerService, recovered);
    }

    // helpers

    private Object followerMojangProxy() throws Exception {
        return followerWorld.loadClass(PKG + "internal.bootstrap.proxy.DemocracyServiceProxies")
                .getMethod("mojangProxy", Object.class)
                .invoke(null, leaderMojangService);
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> leaderSkinMap() throws Exception {
        return (Map<Object, Object>) call(leaderCache, "getUniqueIdentifierToSkinMap");
    }

    private static Object skinOf(ClassLoader world, String value, String signature) throws Exception {
        return world.loadClass(PKG + "api.data.SkinDto")
                .getMethod("of", String.class, String.class)
                .invoke(null, value, signature);
    }

    /** Invokes by name and arity so the caller never mentions foreign parameter types. */
    private static Object call(Object target, String methodName, Object... args) throws Exception {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == args.length) {
                try {
                    return method.invoke(target, args);
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof Exception cause) throw cause;
                    throw e;
                }
            }
        }
        throw new AssertionError("No method " + methodName + "/" + args.length + " on " + target.getClass());
    }

    private static Plugin mockPlugin(String name) {
        Logger logger = Logger.getLogger(name);
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getLogger" -> logger;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "MockPlugin(" + name + ")";
                    default -> throw new UnsupportedOperationException(
                            "Unexpected Plugin call in test: " + method.getName());
                });
    }
}
