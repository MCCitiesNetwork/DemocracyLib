package net.democracycraft.democracyLib.api.bootstrap.contract;

import java.lang.annotation.*;

/**
 * Marks a method as bridgeable across classloaders.
 * <p>
 * This annotation is intended for build-time contract generation.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface BridgeMethod {

    BridgeSide side() default BridgeSide.FOLLOWER_TO_LEADER;

    BridgeStability stability() default BridgeStability.DERIVED_ID;

    /**
     * Optional explicit method id. Only used when {@link #stability()} is {@link BridgeStability#STABLE_ID}.
     */
    String id() default "";

    OverloadPolicy overloads() default OverloadPolicy.ALLOW;

    SignaturePolicy signature() default SignaturePolicy.FULL_SIGNATURE;

    /**
     * Protocol version this method became available.
     */
    int sinceProtocol() default 1;
}

