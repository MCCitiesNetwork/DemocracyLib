package net.democracycraft.democracyLib.api.config;

@Configurable(name = "GeneratedInheritedConfig")
public class InheritedConfig extends TestConfig {

    @ConfigValue(fieldName = "inherited-method", comment = "This is an inherited method")
    public int getInheritedMethod() {
        return 42;
    }
}
