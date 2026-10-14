package dev.phucngu.intellaspring;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.phucngu.intellaspring.beans.BeanDependencies;
import dev.phucngu.intellaspring.beans.BeansTreeModel;
import dev.phucngu.intellaspring.spring.SpringContextService;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;

import java.util.List;

/**
 * The Beans view data layer: tree rows per module, and the dependency edges
 * (depends-on / injected-into) computed from the shared injection rule set.
 */
public class BeansViewTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        addClass("package org.springframework.stereotype;\npublic @interface Component { String value() default \"\"; }");
        addClass("package org.springframework.stereotype;\npublic @interface Service { String value() default \"\"; }");
        addClass("package org.springframework.context.annotation;\npublic @interface Configuration { String value() default \"\"; }");
        addClass("package org.springframework.context.annotation;\npublic @interface Bean { String[] value() default {}; String[] name() default {}; }");
        addClass("package org.springframework.beans.factory.annotation;\npublic @interface Autowired { boolean required() default true; }");
        addClass("package demo;\n@org.springframework.stereotype.Service\npublic class UserService { public String greet() { return \"\"; } }");
        // constructor injection (implicit, no @Autowired) — the modern style
        addClass("package demo;\n@org.springframework.stereotype.Service\npublic class OrderClient {\n"
                + "  private final UserService userService;\n"
                + "  public OrderClient(UserService userService) { this.userService = userService; }\n"
                + "}");
        // field injection via @Autowired
        addClass("package demo;\n@org.springframework.stereotype.Service\npublic class ReportClient {\n"
                + "  @org.springframework.beans.factory.annotation.Autowired\n"
                + "  private UserService userService;\n"
                + "}");
        // @Bean factory parameter
        addClass("package demo;\n@org.springframework.context.annotation.Configuration\npublic class AppConfig {\n"
                + "  @org.springframework.context.annotation.Bean\n"
                + "  public String reportService(UserService userService) { return \"\"; }\n"
                + "}");
    }

    private void addClass(String source) {
        java.util.regex.Matcher pkg = java.util.regex.Pattern.compile("package\\s+([\\w.]+);").matcher(source);
        java.util.regex.Matcher type = java.util.regex.Pattern.compile("(?:class|interface|enum)\\s+(\\w+)").matcher(source);
        assertTrue(type.find());
        String dir = pkg.find() ? pkg.group(1).replace('.', '/') + "/" : "";
        myFixture.addFileToProject(dir + type.group(1) + ".java", source);
    }

    private List<SpringBeanDef> pool() {
        return SpringContextService.getInstance(getProject()).allBeans();
    }

    private static SpringBeanDef byName(List<SpringBeanDef> beans, String name) {
        return beans.stream()
                .filter(b -> name.equals(b.displayName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no bean '" + name + "' among " + beans));
    }

    public void testTreeRowsPerModule() {
        List<BeansTreeModel.ModuleRow> rows = BeansTreeModel.build(getProject());
        assertEquals(1, rows.size());
        List<String> names = rows.get(0).beans().stream().map(BeansTreeModel.BeanRow::name).toList();
        // name-sorted: appConfig (the @Configuration class is a bean too), then the services
        assertEquals(List.of("appConfig", "orderClient", "reportClient", "reportService", "userService"), names);
        assertEquals("UserService", rows.get(0).beans().get(4).typeShort());
    }

    public void testDependsOnSeesAllInjectionStyles() {
        SpringBeanDef userService = byName(pool(), "userService");
        SpringBeanDef orderClient = byName(pool(), "orderClient");
        SpringBeanDef reportService = byName(pool(), "reportService");
        List<String> orderDeps = BeanDependencies.dependsOn(orderClient, pool()).stream()
                .map(BeanDependencies.Dependency::bean).map(SpringBeanDef::displayName).toList();
        assertEquals(List.of("userService"), orderDeps);
        List<String> reportDeps = BeanDependencies.dependsOn(reportService, pool()).stream()
                .map(BeanDependencies.Dependency::bean).map(SpringBeanDef::displayName).toList();
        assertEquals(List.of("userService"), reportDeps);
        assertEquals(List.of(), BeanDependencies.dependsOn(userService, pool()));
    }

    public void testInjectedIntoCollectsEveryDependent() {
        SpringBeanDef userService = byName(pool(), "userService");
        List<String> dependents = BeanDependencies.injectedInto(userService, pool()).stream()
                .map(BeanDependencies.Dependency::bean).map(SpringBeanDef::displayName).toList();
        // the @Bean factory belongs to appConfig, so the dependents are the two services + the config
        assertEquals(List.of("appConfig", "orderClient", "reportClient"), dependents);
    }

    public void testSiteLabelsNameTheInjectionPoint() {
        SpringBeanDef userService = byName(pool(), "userService");
        List<String> labels = BeanDependencies.injectedInto(userService, pool()).stream()
                .map(BeanDependencies.Dependency::siteLabel).toList();
        assertTrue(labels.toString(), labels.contains("OrderClient.userService"));
    }

    /**
     * Regression: Swing list renderers call toString() during paint, outside read actions.
     * The generated record toString touched the PsiType component and crashed the IDE
     * ("Read access is allowed from inside read-action only", 28 SEVERE entries blaming
     * the plugin). toString must be callable from a background thread with no read access.
     */
    public void testToStringIsCallableWithoutReadAccess() throws Exception {
        SpringBeanDef userService = byName(pool(), "userService");
        // Dependency.toString chains SpringBeanDef.toString — exercise both on a bare thread
        BeanDependencies.Dependency dependency = BeanDependencies.injectedInto(userService, pool()).get(0);
        java.util.concurrent.Future<?> task = java.util.concurrent.CompletableFuture.runAsync(
                () -> {
                    assertNotNull(userService.toString());
                    assertNotNull(dependency.toString());
                });
        task.get(10, java.util.concurrent.TimeUnit.SECONDS);
    }
}
