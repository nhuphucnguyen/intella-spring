package dev.phucngu.intellaspring;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.phucngu.intellaspring.spring.SpringContextService;
import dev.phucngu.intellaspring.spring.model.BeanOrigin;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;

import java.util.List;

/**
 * The XML side of the bean model: recognition of Spring config files, name/alias rules,
 * attribute-driven scope/lazy, and graceful handling of classes missing from the classpath.
 */
public class SpringXmlBeanModelTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // The XML scan only runs for Spring modules; one resolvable stereotype is enough.
        addClass("package org.springframework.stereotype;\npublic @interface Component { String value() default \"\"; }");
        addClass("package demo;\npublic class OrderService {}");
        addClass("package demo;\npublic class UserService {}");
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
                        "no bean '" + name + "' among " + beans.stream().map(SpringBeanDef::displayName).sorted().toList()));
    }

    public void testXmlBeansAreCollected() {
        myFixture.addFileToProject("app.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <beans xmlns="http://www.springframework.org/schema/beans">
                    <bean id="orderService" class="demo.OrderService" scope="prototype" lazy-init="true" primary="true"/>
                    <bean name="userSvc, userService userAlias" class="demo.UserService"/>
                </beans>
                """);

        List<SpringBeanDef> beans = beans();
        SpringBeanDef order = byName(beans, "orderService");
        assertEquals(BeanOrigin.XML, order.origin());
        assertNotNull(order.beanType());
        assertEquals("demo.OrderService", order.beanTypeName());
        assertEquals("prototype", order.scope());
        assertTrue(order.lazy());
        assertTrue(order.primary());
        assertFalse(order.isSingleton());

        // name="a, b c": first token is the name, the rest are aliases.
        SpringBeanDef user = byName(beans, "userSvc");
        assertEquals(List.of("userService", "userAlias"), user.aliases());
        assertTrue(user.matchesName("userAlias"));
    }

    public void testAliasTagAddsAnAlias() {
        myFixture.addFileToProject("app.xml", """
                <beans xmlns="http://www.springframework.org/schema/beans">
                    <bean id="orderService" class="demo.OrderService"/>
                    <alias name="orderService" alias="orders"/>
                </beans>
                """);

        SpringBeanDef order = byName(beans(), "orderService");
        assertTrue(order.aliases().contains("orders"));
    }

    public void testUnresolvedClassStillYieldsABean() {
        myFixture.addFileToProject("app.xml", """
                <beans xmlns="http://www.springframework.org/schema/beans">
                    <bean id="broken" class="com.nope.Missing"/>
                </beans>
                """);

        SpringBeanDef broken = byName(beans(), "broken");
        assertNull(broken.beanType());
        assertEquals("com.nope.Missing", broken.beanTypeName());
    }

    public void testNonSpringXmlIsIgnored() {
        myFixture.addFileToProject("plain.xml", "<root><bean/></root>");
        myFixture.addFileToProject("beansLike.xml", "<beans><foo/></beans>");
        myFixture.addFileToProject("nested.xml", """
                <beans xmlns="http://www.springframework.org/schema/beans">
                    <beans profile="dev">
                        <bean id="devOnly" class="demo.OrderService"/>
                    </beans>
                </beans>
                """);

        List<SpringBeanDef> beans = beans();
        assertEquals(List.of("devOnly"), beans.stream().map(SpringBeanDef::displayName).toList());
        assertEquals(List.of("dev"), byName(beans, "devOnly").profiles());
    }
}
