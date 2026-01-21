package net.democracycraft.democracyLib.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Configurable {
    String name();
    String targetPackage() default "";
    ConfigFormat format() default ConfigFormat.YAML;
}

