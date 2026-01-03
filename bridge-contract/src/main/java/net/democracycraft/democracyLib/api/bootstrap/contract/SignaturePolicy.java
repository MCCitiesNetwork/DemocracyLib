package net.democracycraft.democracyLib.api.bootstrap.contract;

/**
 * How strictly methods should be identified.
 */
public enum SignaturePolicy {
    /**
     * Identify methods by name + parameter count only (not recommended).
     */
    ARITY_ONLY,

    /**
     * Identify methods by full parameter type list.
     */
    FULL_SIGNATURE
}
