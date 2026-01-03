package net.democracycraft.democracyLib.api.bootstrap.contract;

import java.lang.annotation.*;

/**
 * Optional human-friendly key for overloaded methods.
 * <p>
 * If present, the generated id can incorporate this key instead of raw parameter type names.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface BridgeOverloadKey {

    String value();
}

