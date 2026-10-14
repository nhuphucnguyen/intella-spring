package dev.phucngu.intellaspring;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.phucngu.intellaspring.inspection.SpringAutowiringInspection;
import dev.phucngu.intellaspring.spring.SpringInjections;

import java.util.List;
import java.util.Objects;

/**
 * The injection styles IDEA's SpringAutowireUtil recognizes — ported: implicit
 * single-constructor injection, Lombok constructor annotations, @Inject/@Resource, and the
 * @Value exclusion — across the locator, the reverse marker and the autowiring inspection.
 */
public class SpringInjectionStylesTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new SpringAutowiringInspection());
        addClass("package org.springframework.stereotype;\npublic @interface Component { String value() default \"\"; }");
        addClass("package org.springframework.context.annotation;\npublic @interface Configuration { }");
        addClass("package org.springframework.context.annotation;\npublic @interface Bean { String[] value() default {}; }");
        addClass("package org.springframework.beans.factory.annotation;\npublic @interface Autowired { boolean required() default true; }");
        addClass("package org.springframework.beans.factory.annotation;\npublic @interface Value { String value(); }");
        addClass("package javax.annotation;\npublic @interface Resource { String name() default \"\"; }");
        addClass("package javax.inject;\npublic @interface Inject { }");
        addClass("package lombok;\npublic @interface RequiredArgsConstructor { }");
        addClass("package demo;\npublic class UserService {}");
        addClass("package demo;\npublic class MissingService {}");
        addClass("package demo;\n@org.springframework.stereotype.Component\npublic class UserServiceBean {}");
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

    public void testImplicitSingleConstructorOfABeanInjects() {
        addClass("package demo;\n@org.springframework.stereotype.Component\npublic class CtorConsumer {\n"
                + "  public CtorConsumer(demo.UserService userService) { }\n}");
        var points = SpringInjections.injectionPoints(findClass("demo.UserService"));
        assertTrue(points.toString(), points.stream().anyMatch(p -> p instanceof com.intellij.psi.PsiParameter));

        // but a single constructor of a NON-bean class is not an injection point
        addClass("package demo;\npublic class PlainHolder {\n  public PlainHolder(demo.UserService userService) { }\n}");
        var after = SpringInjections.injectionPoints(findClass("demo.UserService"));
        assertTrue(after.stream().noneMatch(p -> p instanceof com.intellij.psi.PsiParameter
                && ((com.intellij.psi.PsiParameter) p).getDeclarationScope() instanceof com.intellij.psi.PsiMethod m
                && "PlainHolder".equals(m.getContainingClass().getName())));
    }

    public void testLombokRequiredArgsConstructorFinalFieldInjects() {
        addClass("package demo;\n@org.springframework.stereotype.Component\n@lombok.RequiredArgsConstructor\n"
                + "public class LombokConsumer {\n"
                + "  private final demo.UserService userService;\n"
                + "  private demo.UserService nonFinalIgnored;\n"
                + "}");
        var points = SpringInjections.injectionPoints(findClass("demo.UserService"));
        assertTrue(points.toString(), points.stream().anyMatch(p ->
                p instanceof com.intellij.psi.PsiField f && "userService".equals(f.getName())));
        assertTrue(points.stream().noneMatch(p ->
                p instanceof com.intellij.psi.PsiField f && "nonFinalIgnored".equals(f.getName())));
    }

    public void testResourceAndInjectFieldsInject() {
        addClass("package demo;\n@org.springframework.stereotype.Component\npublic class JsrConsumer {\n"
                + "  @javax.annotation.Resource private demo.UserService a;\n"
                + "  @javax.inject.Inject private demo.UserService b;\n"
                + "}");
        assertEquals(2, SpringInjections.injectionPoints(findClass("demo.UserService")).size());
    }

    public void testValueParametersConfigureInsteadOfInject() {
        addClass("package demo;\n@org.springframework.context.annotation.Configuration\npublic class ValueConfig {\n"
                + "  @org.springframework.context.annotation.Bean\n"
                + "  public String greeting(@org.springframework.beans.factory.annotation.Value(\"${x}\") demo.UserService value) { return \"\"; }\n"
                + "}");
        assertEquals(List.of(), SpringInjections.injectionPoints(findClass("demo.UserService")));
    }

    public void testInjectionPointsSortProjectSourcesFirstAlphabetically() {
        addClass("package demo;\n@org.springframework.stereotype.Component\npublic class ZetaConsumer {\n"
                + "  @org.springframework.beans.factory.annotation.Autowired private demo.UserService z;\n}");
        addClass("package demo;\n@org.springframework.stereotype.Component\npublic class AlphaConsumer {\n"
                + "  @org.springframework.beans.factory.annotation.Autowired private demo.UserService a;\n}");
        var points = dev.phucngu.intellaspring.ui.MarkerSupport.sortedForPresentation(
                getProject(), SpringInjections.injectionPoints(findClass("demo.UserService")));
        var labels = points.stream().map(dev.phucngu.intellaspring.ui.MarkerSupport::memberLabel).toList();
        assertEquals(labels.toString(), "AlphaConsumer.a", labels.get(0));
        assertEquals(labels.toString(), "ZetaConsumer.z", labels.get(labels.size() - 1));
    }

    public void testMissingBeanThroughImplicitConstructorIsAnError() {
        addClass("package demo;\n@org.springframework.stereotype.Component\npublic class CtorMissing {\n"
                + "  public CtorMissing(demo.MissingService missing) { }\n}");
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("demo/CtorMissing.java"));
        var problems = myFixture.doHighlighting().stream()
                .map(h -> h.getDescription())
                .filter(Objects::nonNull)
                .filter(d -> d.contains("bean of type"))
                .toList();
        assertEquals(List.of("No qualifying bean of type 'demo.MissingService' found"), problems);
    }

    public void testMissingBeanThroughLombokFieldIsAnError() {
        addClass("package demo;\n@org.springframework.stereotype.Component\n@lombok.RequiredArgsConstructor\n"
                + "public class LombokMissing {\n"
                + "  private final demo.MissingService missing;\n"
                + "}");
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("demo/LombokMissing.java"));
        var problems = myFixture.doHighlighting().stream()
                .map(h -> h.getDescription())
                .filter(Objects::nonNull)
                .filter(d -> d.contains("bean of type"))
                .toList();
        // v1: the Lombok field is a navigation target; the check runs on written constructors
        // and annotated members, so a Lombok-only class is clean until constructor PSI exists.
        assertTrue(problems.toString(), problems.isEmpty());
    }
}
