package dev.phucngu.intellaspring;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.phucngu.intellaspring.spring.SpringInjections;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;
import dev.phucngu.intellaspring.ui.SpringBeanLineMarkerProvider;

import java.util.List;

/**
 * Injection-point discovery and the gutter marker that consumes it — the first user-visible
 * slice of the model.
 */
public class SpringInjectionsTest extends BasePlatformTestCase {

    private void addClass(String source) {
        java.util.regex.Matcher pkg = java.util.regex.Pattern.compile("package\\s+([\\w.]+);").matcher(source);
        java.util.regex.Matcher type = java.util.regex.Pattern.compile("(?:class|interface|enum)\\s+(\\w+)").matcher(source);
        assertTrue("fixture source must declare a type: " + source, type.find());
        String dir = pkg.find() ? "src/" + pkg.group(1).replace('.', '/') + "/" : "src/";
        myFixture.addFileToProject(dir + type.group(1) + ".java", source);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        addClass("package org.springframework.stereotype;\npublic @interface Component { String value() default \"\"; }");
        addClass("package org.springframework.context.annotation;\npublic @interface Configuration { String value() default \"\"; }");
        addClass("package org.springframework.context.annotation;\npublic @interface Bean { String[] value() default {}; }");
        addClass("package org.springframework.beans.factory.annotation;\npublic @interface Autowired { }");
        addClass("package demo;\npublic class UserService {}");
        addClass("package demo;\npublic class ReportService {}");
        addClass("package demo;\n@org.springframework.stereotype.Component\npublic class Consumer {\n"
                + "  @org.springframework.beans.factory.annotation.Autowired\n"
                + "  private demo.UserService userService;\n"
                + "  @org.springframework.beans.factory.annotation.Autowired\n"
                + "  public void setReportService(demo.ReportService reports) { }\n"
                + "}");
        addClass("package demo;\n@org.springframework.context.annotation.Configuration\npublic class AppConfig {\n"
                + "  @org.springframework.context.annotation.Bean\n"
                + "  public demo.UserService userService() { return new demo.UserService(); }\n"
                + "  @org.springframework.context.annotation.Bean\n"
                + "  public demo.ReportService reportService(demo.UserService userService) { return new demo.ReportService(); }\n"
                + "}");
    }

    private com.intellij.psi.PsiClass findClass(String fqn) {
        com.intellij.psi.PsiClass found = com.intellij.psi.JavaPsiFacade.getInstance(getProject())
                .findClass(fqn, com.intellij.psi.search.GlobalSearchScope.moduleScope(getModule()));
        assertNotNull("fixture class must resolve: " + fqn, found);
        return found;
    }

    public void testInjectionPointsCoverFieldsSettersAndBeanMethodParameters() {
        var userServicePoints = SpringInjections.injectionPoints(findClass("demo.UserService"));
        // The @Autowired field in Consumer and the @Bean factory parameter in AppConfig.
        assertEquals(2, userServicePoints.size());
        assertTrue(userServicePoints.stream().anyMatch(p -> p instanceof com.intellij.psi.PsiField));
        assertTrue(userServicePoints.stream().anyMatch(p -> p instanceof com.intellij.psi.PsiParameter));

        // A setter parameter injected without its own annotation, via the @Autowired method.
        var reportPoints = SpringInjections.injectionPoints(findClass("demo.ReportService"));
        assertTrue(reportPoints.stream().anyMatch(p -> p instanceof com.intellij.psi.PsiParameter
                && p.getParent() != null));
    }

    public void testGutterMarkerOnStereotypeClassAndBeanMethod() {
        var provider = new SpringBeanLineMarkerProvider();
        var marker = provider.getLineMarkerInfo(findClass("demo.Consumer").getNameIdentifier());
        assertNotNull("stereotype class must carry the bean gutter icon", marker);
        assertEquals("Spring bean: consumer", marker.createGutterRenderer().getTooltipText());

        var methodMarker = provider.getLineMarkerInfo(findClass("demo.AppConfig").findMethodsByName("userService", false)[0]
                .getNameIdentifier());
        assertNotNull("@Bean method must carry the bean gutter icon", methodMarker);
        assertEquals("Spring bean: userService", methodMarker.createGutterRenderer().getTooltipText());
    }

    public void testReverseMarkerOnAutowiredFieldLeadsToBean() {
        com.intellij.psi.PsiClass consumer = findClass("demo.Consumer");
        com.intellij.psi.PsiField field = consumer.findFieldByName("userService", false);
        assertNotNull(field);
        var marker = new dev.phucngu.intellaspring.ui.SpringInjectionLineMarkerProvider()
                .getLineMarkerInfo(field.getNameIdentifier());
        assertNotNull("@Autowired field must carry the reverse marker", marker);
        assertTrue(marker.createGutterRenderer().getTooltipText(),
                marker.createGutterRenderer().getTooltipText().contains("userService"));
    }

    public void testXmlBeanTagCarriesMarker() {
        myFixture.addFileToProject("app.xml", """
                <beans xmlns="http://www.springframework.org/schema/beans">
                    <bean id="orderService" class="demo.ReportService"/>
                </beans>
                """);
        var file = (com.intellij.psi.xml.XmlFile) com.intellij.psi.PsiManager.getInstance(getProject())
                .findFile(myFixture.findFileInTempDir("app.xml"));
        var tag = file.getRootTag().findSubTags("bean")[0];
        com.intellij.psi.PsiElement nameToken = null;
        for (var child : tag.getChildren()) {
            if (child instanceof com.intellij.psi.xml.XmlToken t
                    && t.getTokenType() == com.intellij.psi.xml.XmlTokenType.XML_NAME) {
                nameToken = child;
                break;
            }
        }
        assertNotNull(nameToken);
        var marker = new dev.phucngu.intellaspring.ui.SpringXmlBeanLineMarkerProvider()
                .getLineMarkerInfo(nameToken);
        assertNotNull("bean tag must carry the gutter icon", marker);
        assertEquals("Spring bean: orderService", marker.createGutterRenderer().getTooltipText());
    }

    public void testNoGutterMarkerOnPlainClass() {
        var provider = new SpringBeanLineMarkerProvider();
        assertNull("plain class is not a bean", provider.getLineMarkerInfo(findClass("demo.ReportService").getNameIdentifier()));
    }
}
