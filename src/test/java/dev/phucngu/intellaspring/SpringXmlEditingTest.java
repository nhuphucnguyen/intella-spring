package dev.phucngu.intellaspring;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.phucngu.intellaspring.xml.SpringXmlCompletionContributor;

import java.util.List;

/**
 * References and completion inside Spring XML: bean-name references resolve against the model
 * (annotations and XML beans are one namespace), unknown names stay unresolved (red), class
 * attributes navigate softly, and property names complete from the bean class's setters.
 */
public class SpringXmlEditingTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        addClass("package org.springframework.stereotype;\npublic @interface Component { String value() default \"\"; }");
        addClass("package demo;\npublic class UserService {\n"
                + "  public void setPort(int port) { }\n"
                + "  public void setURLBase(String base) { }\n"
                + "  public void unrelated() { }\n"
                + "}");
        myFixture.addFileToProject("src/app.xml", """
                <beans xmlns="http://www.springframework.org/schema/beans">
                    <bean id="userService" class="demo.UserService"/>
                    <bean id="orderService" class="demo.OrderService"/>
                </beans>
                """);
    }

    private void addClass(String source) {
        java.util.regex.Matcher pkg = java.util.regex.Pattern.compile("package\\s+([\\w.]+);").matcher(source);
        java.util.regex.Matcher type = java.util.regex.Pattern.compile("(?:class|interface|enum)\\s+(\\w+)").matcher(source);
        assertTrue(type.find());
        String dir = pkg.find() ? "src/" + pkg.group(1).replace('.', '/') + "/" : "src/";
        myFixture.addFileToProject(dir + type.group(1) + ".java", source);
    }

    public void testBeanRefAttributeResolvesToTheDeclaration() {
        myFixture.configureByText("use.xml", """
                <beans xmlns="http://www.springframework.org/schema/beans">
                    <bean id="a" class="demo.UserService">
                        <property name="port" ref="userServ<caret>ice"/>
                    </bean>
                </beans>
                """);
        var reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull(reference);
        var target = reference.resolve();
        assertNotNull("bean ref must resolve to the XML declaration", target);
        assertTrue(target.getText(), target.getText().contains("demo.UserService"));
    }

    public void testAnnotationBeanResolvesFromXmlRef() {
        addClass("package demo;\n@org.springframework.stereotype.Component(\"auditService\")\npublic class AuditService {}");
        myFixture.configureByText("use.xml", """
                <beans xmlns="http://www.springframework.org/schema/beans">
                    <bean id="a" class="demo.UserService">
                        <property name="port" ref="auditServ<caret>ice"/>
                    </bean>
                </beans>
                """);
        var reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull(reference);
        assertNotNull("XML ref to an annotation-declared bean must resolve",
                reference.resolve());
    }

    public void testUnknownBeanNameStaysUnresolved() {
        myFixture.configureByText("use.xml", """
                <beans xmlns="http://www.springframework.org/schema/beans">
                    <bean id="a" class="demo.UserService">
                        <property name="port" ref="nope"/>
                    </bean>
                </beans>
                """);
        var file = myFixture.getFile();
        // caret-free variant: locate the attribute value by text
        int at = file.getText().indexOf("\"nope\"") + 1;
        var reference = file.findReferenceAt(at);
        assertNotNull(reference);
        assertNull("unknown bean name must not resolve", reference.resolve());
    }

    public void testClassAttributeNavigatesSoftly() {
        myFixture.configureByText("use.xml", """
                <beans xmlns="http://www.springframework.org/schema/beans">
                    <bean id="a" class="demo.UserServ<caret>ice"/>
                </beans>
                """);
        var reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull(reference);
        var target = reference.resolve();
        assertNotNull(target);
        assertTrue(target.toString(), target.toString().contains("UserService"));
    }

    public void testPropertyNameCompletionFromSetters() {
        myFixture.configureByText("use.xml", """
                <beans xmlns="http://www.springframework.org/schema/beans">
                    <bean id="a" class="demo.UserService">
                        <property name="<caret>"/>
                    </bean>
                </beans>
                """);
        myFixture.completeBasic();
        var items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertTrue(items.toString(), items.contains("port"));
        assertTrue(items.toString(), items.contains("URLBase"));
        assertFalse(items.toString(), items.contains("unrelated"));
    }

    public void testWritablePropertiesHelper() {
        var psi = com.intellij.psi.JavaPsiFacade.getInstance(getProject())
                .findClass("demo.UserService", com.intellij.psi.search.GlobalSearchScope.moduleScope(getModule()));
        assertNotNull(psi);
        assertEquals(List.of("port", "URLBase"), SpringXmlCompletionContributor.writableProperties(psi));
    }
}
