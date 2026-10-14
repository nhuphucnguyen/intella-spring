package dev.phucngu.intellaspring;

import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.phucngu.intellaspring.boot.IntellaSpringBootConfigurationType;
import dev.phucngu.intellaspring.boot.IntellaSpringBootRunConfiguration;
import dev.phucngu.intellaspring.boot.SpringBootApplicationDetector;

/**
 * The Spring Boot run configuration: type registration, main-class validation through the
 * @SpringBootApplication meta chain, and the Boot VM-parameter conventions.
 */
public class BootRunConfigurationTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        addClass("package org.springframework.context.annotation;\npublic @interface Configuration { }");
        addClass("package org.springframework.boot;\n@org.springframework.context.annotation.Configuration\npublic @interface SpringBootConfiguration {}");
        addClass("package org.springframework.boot.autoconfigure;\n@org.springframework.boot.SpringBootConfiguration\npublic @interface SpringBootApplication {}");
        addClass("package demo;\npublic class PlainMain {\n  public static void main(String[] args) { }\n}");
        addClass("package demo;\n@org.springframework.boot.autoconfigure.SpringBootApplication\npublic class DemoApplication {\n  public static void main(String[] args) { }\n}");
        addClass("package demo;\n@org.springframework.boot.autoconfigure.SpringBootApplication\npublic class BootNoMain { }");
    }

    private void addClass(String source) {
        java.util.regex.Matcher pkg = java.util.regex.Pattern.compile("package\\s+([\\w.]+);").matcher(source);
        java.util.regex.Matcher type = java.util.regex.Pattern.compile("(?:class|interface|enum)\\s+(\\w+)").matcher(source);
        assertTrue(type.find());
        String dir = pkg.find() ? pkg.group(1).replace('.', '/') + "/" : "";
        myFixture.addFileToProject(dir + type.group(1) + ".java", source);
    }

    private com.intellij.psi.PsiClass findClass(String fqn) {
        return com.intellij.psi.JavaPsiFacade.getInstance(getProject())
                .findClass(fqn, com.intellij.psi.search.GlobalSearchScope.moduleScope(getModule()));
    }

    public void testTypeIsRegistered() {
        assertEquals(IntellaSpringBootConfigurationType.ID,
                ConfigurationTypeUtil.findConfigurationType(IntellaSpringBootConfigurationType.class).getId());
    }

    public void testMetaChainDetectsSpringBootApplication() {
        var scope = com.intellij.psi.search.GlobalSearchScope.allScope(getProject());
        assertTrue(SpringBootApplicationDetector.isSpringBootApplication(findClass("demo.DemoApplication"), scope));
        assertFalse(SpringBootApplicationDetector.isSpringBootApplication(findClass("demo.PlainMain"), scope));
        assertTrue(SpringBootApplicationDetector.hasMainMethod(findClass("demo.DemoApplication")));
        assertFalse(SpringBootApplicationDetector.hasMainMethod(findClass("demo.BootNoMain")));
    }

    public void testCheckConfigurationAcceptsBootAppAndRejectsPlainMain() throws Exception {
        var type = ConfigurationTypeUtil.findConfigurationType(IntellaSpringBootConfigurationType.class);
        // construct directly: cached template configurations can outlive a previous fixture's temp dir
        var config = new IntellaSpringBootRunConfiguration(getProject(), type.getConfigurationFactories()[0], "test");
        config.setModule(getModule());
        config.setWorkingDirectory(null);
        config.setMainClassName("demo.DemoApplication");
        config.checkConfiguration();

        config.setMainClassName("demo.PlainMain");
        try {
            config.checkConfiguration();
            fail("plain main class must be rejected");
        } catch (com.intellij.execution.configurations.RuntimeConfigurationError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("@SpringBootApplication"));
        }
    }

    public void testBootVmParameterConventions() {
        var vm = new com.intellij.execution.configurations.ParametersList();
        IntellaSpringBootRunConfiguration.applyBootParameters(vm, true, true, "dev, local");
        assertTrue(vm.getParameters().toString(), vm.getParameters().contains("-XX:TieredStopAtLevel=1"));
        assertTrue(vm.getParameters().toString(), vm.getParameters().contains("-Dspring.main.banner-mode=OFF"));
        assertTrue(vm.getParameters().toString(), vm.getParameters().contains("-Dspring.profiles.active=dev, local"));
        assertTrue(vm.getParameters().toString(), vm.getParameters().contains("-Dspring.output.ansi.enabled=always"));

        var minimal = new com.intellij.execution.configurations.ParametersList();
        IntellaSpringBootRunConfiguration.applyBootParameters(minimal, false, false, "");
        assertEquals(java.util.List.of("-Dspring.output.ansi.enabled=always"), minimal.getParameters());
    }

    public void testSettingsEditorRoundTripPreservesBootOptions() throws Exception {
        var type = ConfigurationTypeUtil.findConfigurationType(IntellaSpringBootConfigurationType.class);
        var config = new dev.phucngu.intellaspring.boot.IntellaSpringBootRunConfiguration(
                getProject(), type.getConfigurationFactories()[0], "test");
        config.setMainClassName("demo.DemoApplication");
        config.setActiveProfiles("dev, local");
        config.setHideBanner(false);
        var editor = new dev.phucngu.intellaspring.boot.IntellaSpringBootSettingsEditor(config);
        editor.resetFrom(config);
        editor.applyTo(config);
        assertEquals("demo.DemoApplication", config.getMainClassName());
        assertEquals("dev, local", config.getActiveProfiles());
        assertFalse(config.isHideBanner());
        editor.dispose();
    }

    public void testSuggestedNameUsesShortClass() {
        var type = ConfigurationTypeUtil.findConfigurationType(IntellaSpringBootConfigurationType.class);
        var config = new IntellaSpringBootRunConfiguration(getProject(), type.getConfigurationFactories()[0], "test");
        config.setMainClassName("com.example.orders.OrdersApplication");
        assertEquals("OrdersApplication", config.suggestedName());
    }
}
