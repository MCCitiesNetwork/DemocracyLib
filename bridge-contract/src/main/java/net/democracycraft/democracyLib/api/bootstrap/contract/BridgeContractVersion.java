package net.democracycraft.democracyLib.api.bootstrap.contract;

import java.lang.annotation.*;

/**
 * Declares the intended bridge contract protocol version for build-time validation and contract generation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface BridgeContractVersion {

    int value();
}

