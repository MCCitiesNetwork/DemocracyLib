package net.democracycraft.democracyLib.api.bootstrap.contract;

import java.lang.annotation.*;

/**
 * Marks a String constant as a shared JVM anchor key.
 * <p>
 * This annotation is intended for build-time contract generation.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface BridgeAnchorKey {

    BridgeStability stability() default BridgeStability.STABLE_ID;

    /**
     * Optional logical id for the key (distinct from the actual String value).
     */
    String id() default "";
}

