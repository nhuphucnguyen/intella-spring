package dev.phucngu.intellaspring;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.phucngu.intellaspring.spring.SpringContextService;
import dev.phucngu.intellaspring.spring.model.BeanOrigin;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;

import java.util.List;

/**
 * The bean model over Java sources. The Spring annotations are declared in fixture sources
 * under their real FQNs — the scanner is FQN-driven, so it behaves identically with the
 * annotations coming from a library in a real project.
 */
public class SpringBeanModelTest extends BasePlatformTestCase {

    /** Minimal, shape-compatible copies of the Spring API surface the model reads. */
    private void springApi() {
        addClass("package org.springframework.stereotype;\npublic @interface Component { String value() default \"\"; }");
        addClass("package org.springframework.stereotype;\npublic @interface Service { String value() default \"\"; }");
        addClass("package org.springframework.context.annotation;\npublic @interface Configuration { String value() default \"\"; }");
        addClass("package org.springframework.context.annotation;\npublic @interface Bean { String[] value() default {}; String[] name() default {}; }");
        addClass("package org.springframework.context.annotation;\npublic @interface Primary { }");
        addClass("package org.springframework.context.annotation;\npublic @interface Lazy { }");
        addClass("package org.springframework.context.annotation;\npublic @interface Scope { String value() default \"singleton\"; }");
        addClass("package org.springframework.context.annotation;\npublic @interface Profile { String[] value(); }");
        addClass("package org.springframework.beans.factory.annotation;\npublic @interface Qualifier { String value(); }");
    }

    /** The fixture's old addClass(String) is gone on 2026.2; files under src/ index the same way. */
    private void addClass(String source) {
        java.util.regex.Matcher pkg = java.util.regex.Pattern.compile("package\\s+([\\w.]+);").matcher(source);
        java.util.regex.Matcher type = java.util.regex.Pattern.compile("(?:class|interface|enum)\\s+(\\w+)").matcher(source);
        assertTrue("fixture source must declare a type: " + source, type.find());
        String dir = pkg.find() ? "src/" + pkg.group(1).replace('.', '/') + "/" : "src/";
        myFixture.addFileToProject(dir + type.group(1) + ".java", source);
    }

    private List<SpringBeanDef> beans() {
        return SpringContextService.getInstance(getProject()).beans(getModule());
    }

    private static SpringBeanDef byName(List<SpringBeanDef> beans, String name) {
        return beans.stream()
                .filter(b -> name.equals(b.beanName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no bean '" + name + "' among " + names(beans)));
    }

    private static List<String> names(List<SpringBeanDef> beans) {
        return beans.stream().map(SpringBeanDef::displayName).sorted().toList();
    }

    public void testStereotypeClassesBecomeBeans() {
        springApi();
        addClass("package demo;\n@org.springframework.stereotype.Component\npublic class UserService {}");
        addClass("package demo;\n@org.springframework.stereotype.Component(\"custom\")\npublic class AuditService {}");
        addClass("package demo;\n@org.springframework.stereotype.Service\npublic class URLFetcher {}");

        List<SpringBeanDef> beans = beans();
        // JavaBeans decapitalize: two leading capitals stay as-is.
        assertEquals(List.of("URLFetcher", "custom", "userService"), names(beans));
        SpringBeanDef user = byName(beans, "userService");
        assertEquals(BeanOrigin.STEREOTYPE, user.origin());
        assertEquals("org.springframework.stereotype.Component", user.stereotype());
        assertEquals("demo.UserService", user.beanTypeName());
        assertNotNull(user.beanType());
        assertTrue(user.isSingleton());
        assertEquals("custom", byName(beans, "custom").beanName());
    }

    public void testCustomStereotypeViaMetaAnnotation() {
        springApi();
        addClass("package demo;\n@org.springframework.stereotype.Component\npublic @interface MyService {}");
        addClass("package demo;\n@demo.MyService\npublic class PaymentService {}");

        SpringBeanDef payment = byName(beans(), "paymentService");
        assertEquals(BeanOrigin.STEREOTYPE, payment.origin());
        // The annotation sitting directly on the class is the reported stereotype.
        assertEquals("demo.MyService", payment.stereotype());
    }

    public void testSpringBootApplicationIsABeanThroughTheMetaChain() {
        springApi();
        addClass("package org.springframework.boot;\n@org.springframework.context.annotation.Configuration\npublic @interface SpringBootConfiguration {}");
        addClass("package org.springframework.boot.autoconfigure;\n@org.springframework.boot.SpringBootConfiguration\npublic @interface SpringBootApplication {}");
        addClass("package demo;\n@org.springframework.boot.autoconfigure.SpringBootApplication\npublic class DemoApplication {}");

        SpringBeanDef app = byName(beans(), "demoApplication");
        assertEquals("org.springframework.boot.autoconfigure.SpringBootApplication", app.stereotype());
    }

    public void testBeanMethods() {
        springApi();
        addClass("package demo;\npublic class UserService {}");
        addClass("package demo;\n@org.springframework.context.annotation.Configuration\npublic class AppConfig {\n"
                + "  @org.springframework.context.annotation.Bean\n"
                + "  public demo.UserService plainUserService() { return new demo.UserService(); }\n"
                + "  @org.springframework.context.annotation.Bean({\"ds\", \"dataSource\"})\n"
                + "  public demo.UserService dataSource() { return new demo.UserService(); }\n"
                + "  @org.springframework.context.annotation.Bean\n"
                + "  @org.springframework.context.annotation.Profile(\"prod\")\n"
                + "  public demo.UserService prodService() { return new demo.UserService(); }\n"
                + "}");

        List<SpringBeanDef> beans = beans();
        // The configuration class itself is a bean too; @Bean({"ds","dataSource"}) is one
        // bean named "ds" with "dataSource" as an alias.
        assertEquals(List.of("appConfig", "ds", "plainUserService", "prodService"), names(beans));
        SpringBeanDef plain = byName(beans, "plainUserService");
        assertEquals(BeanOrigin.BEAN_METHOD, plain.origin());
        assertEquals("demo.UserService", plain.beanTypeName());
        SpringBeanDef ds = byName(beans, "ds");
        assertEquals(List.of("dataSource"), ds.aliases());
        assertTrue(ds.matchesName("dataSource"));
        assertEquals(List.of("prod"), byName(beans, "prodService").profiles());
    }

    public void testQualifiersScopeProfilesPrimaryLazy() {
        springApi();
        addClass("package demo;\n@org.springframework.stereotype.Component\n"
                + "@org.springframework.beans.factory.annotation.Qualifier(\"main\")\n"
                + "@org.springframework.context.annotation.Primary\n"
                + "@org.springframework.context.annotation.Lazy\n"
                + "@org.springframework.context.annotation.Scope(\"prototype\")\n"
                + "@org.springframework.context.annotation.Profile({\"dev\", \"!cloud\"})\n"
                + "public class RepoService {}");

        SpringBeanDef repo = byName(beans(), "repoService");
        assertEquals(List.of("main"), repo.qualifiers());
        assertTrue(repo.primary());
        assertTrue(repo.lazy());
        assertEquals("prototype", repo.scope());
        assertFalse(repo.isSingleton());
        assertEquals(List.of("dev", "!cloud"), repo.profiles());
    }

    public void testIsSpringModuleFollowsClasspath() {
        // Without any Spring annotation resolvable, the module is not a Spring module.
        addClass("package demo;\npublic class Plain {}");
        assertFalse(SpringContextService.getInstance(getProject()).isSpringModule(getModule()));
        assertTrue(beans().isEmpty());

        springApi();
        addClass("package demo;\n@org.springframework.stereotype.Component\npublic class UserService {}");
        assertTrue(SpringContextService.getInstance(getProject()).isSpringModule(getModule()));
    }

    public void testBeansAreCachedUntilPsiChanges() {
        springApi();
        addClass("package demo;\n@org.springframework.stereotype.Component\npublic class UserService {}");
        List<SpringBeanDef> first = beans();
        assertSame(first, beans());
    }
}
