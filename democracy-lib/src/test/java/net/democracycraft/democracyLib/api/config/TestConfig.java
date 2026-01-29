package net.democracycraft.democracyLib.api.config;

@Configurable(name = "GeneratedTestConfig")
public class TestConfig {

    @ConfigValue(fieldName = "test-value", comment = "This is a test value")
    private String testValue;

    @ConfigValue(fieldName = "test-int", comment = "This is a test integer")
    private int testInt;

    @ConfigValue(fieldName = "test-method", comment = "This is a test method")
    public String getTestMethod() {
        return "methodValue";
    }
}

