package net.democracycraft.democracyLib.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a stable configuration file name for a {@link DemocracyConfig} DTO.
 * <p>
 * Use this when a configuration type must keep a specific file name for backwards compatibility.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DemocracyConfigName {

    /**
     * File name (e.g. {@code "github-gist.yml"}).
     */
    String value();
}

