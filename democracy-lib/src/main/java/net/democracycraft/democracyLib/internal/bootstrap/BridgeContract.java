package net.democracycraft.democracyLib.internal.bootstrap;

import net.democracycraft.democracyLib.api.bootstrap.contract.BridgeAnchorKey;

/**
 * Centralized contract for cross-classloader reflection bridges.
 * <p>
 * Do not inline string literals for method names or anchor keys in bridge code.
 * Keeping all names here makes refactors survivable
 */
public final class BridgeContract {

    private BridgeContract() {
    }

    /**
     * JVM anchor keys.
     */
    public static final class AnchorKeys {
        private AnchorKeys() {
        }

        @BridgeAnchorKey public static final String LEADER = "leader";
        @BridgeAnchorKey public static final String PROTOCOL = "protocol";
        @BridgeAnchorKey public static final String LEADER_PLUGIN = "leaderPlugin";
        @BridgeAnchorKey public static final String LEADER_CLASS = "leaderClass";
        @BridgeAnchorKey public static final String LEADER_SERVICE_MANAGER = "leaderServiceManager";
        @BridgeAnchorKey public static final String LEADER_PLUGIN_REF = "leaderPluginRef";
        @BridgeAnchorKey public static final String PROVIDER_FACTORY = "providerFactory";
        @BridgeAnchorKey public static final String FOLLOWERS = "followers";
        @BridgeAnchorKey public static final String LOCK = "lock";
    }

    /**
     * Stable contract IDs used for cross-classloader bridge lookups.
     * <p>
     * These values must match the {@code id=} declared in {@code @BridgeMethod(stability = STABLE_ID)}
     * on the bridgeable API surfaces.
     */
    public static final class Ids {
        private Ids() {
        }

        public static final class LibApi {
            private LibApi() {
            }

            public static final String GET_MOJANG_SERVICE = "DLIB_API_GET_MOJANG_SERVICE";
            public static final String GET_GITHUB_GIST_SERVICE = "DLIB_API_GET_GITHUB_GIST_SERVICE";
            public static final String GET_GITHUB_GIST_SERVICE_WITH_CONFIG = "DLIB_API_GET_GITHUB_GIST_SERVICE_WITH_CONFIG";
            public static final String GET_SERVICE_MANAGER = "DLIB_API_GET_SERVICE_MANAGER";
            public static final String SHUTDOWN = "DLIB_API_SHUTDOWN";
        }

        public static final class ServiceManager {
            private ServiceManager() {
            }

            public static final String GET_ALL_SERVICES = "DSM_GET_ALL_SERVICES";
            public static final String GET_SERVICES_BY_TYPE = "DSM_GET_SERVICES_BY_TYPE";
            public static final String GET_SERVICE = "DSM_GET_SERVICE";
            public static final String GET_PLUGIN_BOUND_SERVICES = "DSM_GET_PLUGIN_BOUND_SERVICES";
            public static final String REGISTER_SERVICE = "DSM_REGISTER_SERVICE";
            public static final String HAS_REGISTERED_SERVICE = "DSM_HAS_REGISTERED_SERVICE";
            public static final String GET_SERVICE_FOR_PLUGIN = "DSM_GET_SERVICE_FOR_PLUGIN";
        }
    }
}
