package net.democracycraft.democracyLib.internal.bootstrap.bridge;

import java.io.IOException;
import java.io.InputStream;

/**
 * Simulates one plugin's shaded copy of DemocracyLib: defines every
 * {@code net.democracycraft.democracyLib.*} class freshly from the test classpath bytes,
 * while delegating everything else (JDK, Bukkit, Gson, …) to the shared parent loader —
 * exactly the visibility a Paper {@code PluginClassLoader} gives a shaded, unrelocated library.
 * <p>
 * Two instances of this loader produce two copies of every library class with identical
 * fully-qualified names, which is the environment the leader/follower bridge must survive.
 */
final class ShadedCopyClassLoader extends ClassLoader {

    private final ClassLoader resourceSource;

    ShadedCopyClassLoader(ClassLoader parent) {
        super(parent);
        this.resourceSource = parent;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!name.startsWith(BridgeTypeContract.LIBRARY_PACKAGE_PREFIX)) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> defined = findLoadedClass(name);
            if (defined == null) {
                byte[] bytes = readClassBytes(name);
                defined = defineClass(name, bytes, 0, bytes.length);
            }
            if (resolve) resolveClass(defined);
            return defined;
        }
    }

    private byte[] readClassBytes(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/') + ".class";
        try (InputStream in = resourceSource.getResourceAsStream(path)) {
            if (in == null) throw new ClassNotFoundException(name);
            return in.readAllBytes();
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }
}
