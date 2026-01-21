package net.democracycraft.democracyLib.api.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @TempDir
    File tempDir;

    @Test
    void testGeneratedClassExistsAndWorks() throws Exception {
        String generatedClassName = "net.democracycraft.democracyLib.api.config.GeneratedTestConfig";

        Class<?> generatedClass;
        try {
            generatedClass = Class.forName(generatedClassName);
        } catch (ClassNotFoundException e) {
            fail("The generated class " + generatedClassName + " was not found. Annotation Processor might have failed.");
            return;
        }

        assertNotNull(generatedClass, "Generated class should load");
        assertTrue(GeneratedConfig.class.isAssignableFrom(generatedClass), "Generated class should implement GeneratedConfig");

        // public static TestConfigGenerated create(File file)
        Method createMethod = generatedClass.getMethod("create", File.class);
        assertNotNull(createMethod, "create(File) method is missing");

        File configFile = new File(tempDir, "test-config.yml");
        Object configInstance = createMethod.invoke(null, configFile);

        assertNotNull(configInstance, "create() should return an instance");
        assertEquals(generatedClassName, configInstance.getClass().getName());

        // Verify the GeneratedConfig interface methods work
        GeneratedConfig genConfig = (GeneratedConfig) configInstance;

        // This should create the file
        genConfig.loadOrCreate();

        assertTrue(configFile.exists(), "loadOrCreate() should create the file on disk");
    }
}


