package net.democracycraft.democracyLib.api.bootstrap.contract;

import java.lang.annotation.*;

/**
 * Marks a type as part of the reflection bridge surface.
 * <p>
 * This annotation is intended for build-time contract generation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface BridgeApi {

    /**
     * Stable logical namespace for the API. Prefer an enum to avoid typos.
     */
    BridgeNamespace value();
}

