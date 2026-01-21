package net.democracycraft.democracyLib.api.config;

@Configurable(name = "GeneratedTestConfig")
public class TestConfig {

    @ConfigValue(fieldName = "test-value")
    private String testValue;

    @ConfigValue(fieldName = "test-int")
    private int testInt;
}


