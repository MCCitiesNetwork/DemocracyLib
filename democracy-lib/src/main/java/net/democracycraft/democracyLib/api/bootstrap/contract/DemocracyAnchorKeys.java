package net.democracycraft.democracyLib.api.bootstrap.contract;


public final class DemocracyAnchorKeys {

    private DemocracyAnchorKeys() {}

    @BridgeAnchorKey
    public static final String LEADER = "democracylib.leader";

    @BridgeAnchorKey
    public static final String PROTOCOL = "democracylib.protocol";

    @BridgeAnchorKey
    public static final String LEADER_PLUGIN = "democracylib.leaderPlugin";

    @BridgeAnchorKey
    public static final String LEADER_PLUGIN_REF = "democracylib.leaderPluginRef";

    @BridgeAnchorKey
    public static final String LEADER_CLASS = "democracylib.leaderClass";

    @BridgeAnchorKey
    public static final String LEADER_SERVICE_MANAGER = "democracylib.leaderServiceManager";

    @BridgeAnchorKey
    public static final String PROVIDER_FACTORY = "democracylib.providerFactory";

    @BridgeAnchorKey
    public static final String FOLLOWERS = "democracylib.followers";

    @BridgeAnchorKey
    public static final String LOCK = "democracylib.lock";
}

