package dev.phucngu.intellaspring;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.phucngu.intellaspring.inspection.SpringAutowiringInspection;
import dev.phucngu.intellaspring.spring.SpringBeanMatching;
import dev.phucngu.intellaspring.spring.SpringContextService;
import dev.phucngu.intellaspring.spring.SpringInjections;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;
import dev.phucngu.intellaspring.ui.SpringInjectionLineMarkerProvider;

import java.util.List;
import java.util.Objects;

/**
 * The single bean-matching API: subtype-aware type matching, qualifiers (@Qualifier, @Named,
 * custom), @Resource by name, @Primary/@Fallback narrowing, generic @Bean return types.
 */
public class SpringBeanMatchingTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new SpringAutowiringInspection());
        addClass("package org.springframework.stereotype;\npublic @interface Component { String value() default \"\"; }");
        addClass("package org.springframework.stereotype;\npublic @interface Service { String value() default \"\"; }");
        addClass("package org.springframework.context.annotation;\npublic @interface Configuration { }");
        addClass("package org.springframework.context.annotation;\npublic @interface Primary { }");
        addClass("package org.springframework.context.annotation;\npublic @interface Fallback { }");
        addClass("package org.springframework.context.annotation;\npublic @interface Bean { String[] value() default {}; }");
        addClass("package org.springframework.beans.factory.annotation;\npublic @interface Autowired { boolean required() default true; }");
        addClass("package org.springframework.beans.factory.annotation;\npublic @interface Qualifier { String value() default \"\"; }");
        addClass("package jakarta.inject;\npublic @interface Named { String value() default \"\"; }");
        addClass("package jakarta.annotation;\npublic @interface Resource { String name() default \"\"; }");
    }

    private void addClass(String source) {
        java.util.regex.Matcher pkg = java.util.regex.Pattern.compile("package\\s+([\\w.]+);").matcher(source);
        java.util.regex.Matcher type = java.util.regex.Pattern.compile("(?:class|interface|enum)\\s+(\\w+)").matcher(source);
        assertTrue(type.find());
        String dir = pkg.find() ? pkg.group(1).replace('.', '/') + "/" : "src/";
        myFixture.addFileToProject(dir + type.group(1) + ".java", source);
    }

    private PsiClass findClass(String fqn) {
        return com.intellij.psi.JavaPsiFacade.getInstance(getProject())
                .findClass(fqn, com.intellij.psi.search.GlobalSearchScope.moduleScope(getModule()));
    }

    private List<String> beanProblems(String path) {
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir(path));
        return myFixture.doHighlighting().stream()
                .map(info -> info.getDescription())
                .filter(Objects::nonNull)
                .filter(d -> d.contains("bean of type"))
                .toList();
    }

    private void consumer(String fieldDeclaration) {
        addClass("package demo;\n@org.springframework.stereotype.Component\npublic class Consumer {\n  "
                + fieldDeclaration + "\n}");
    }

    private void addInterfaceWithImpls(String... implAnnotations) {
        addClass("package demo;\npublic interface Greeter {}");
        for (int i = 0; i < implAnnotations.length; i++) {
            addClass("package demo;\n@org.springframework.stereotype.Component\n" + implAnnotations[i]
                    + "\npublic class Impl" + (char) ('A' + i) + " implements Greeter {}");
        }
    }

    public void testInterfaceInjectionResolvesToImplementation() {
        addInterfaceWithImpls("");
        consumer("@org.springframework.beans.factory.annotation.Autowired demo.Greeter greeter;");
        assertEquals(List.of(), beanProblems("demo/Consumer.java"));
    }

    public void testGutterOnInterfaceInjectionListsImplementation() {
        addInterfaceWithImpls("");
        consumer("@org.springframework.beans.factory.annotation.Autowired demo.Greeter greeter;");
        PsiField field = findClass("demo.Consumer").findFieldByName("greeter", false);
        var marker = new SpringInjectionLineMarkerProvider().getLineMarkerInfo(field.getNameIdentifier());
        assertNotNull("interface injection must carry the reverse marker", marker);
        assertTrue(marker.createGutterRenderer().getTooltipText(),
                marker.createGutterRenderer().getTooltipText().contains("implA"));
    }

    public void testBeanSideFindsInjectionThroughSupertype() {
        addInterfaceWithImpls("");
        consumer("@org.springframework.beans.factory.annotation.Autowired demo.Greeter greeter;");
        List<PsiElement> points = SpringInjections.injectionPoints(findClass("demo.ImplA"));
        assertEquals(1, points.size());
        assertEquals("greeter", ((PsiField) points.get(0)).getName());
    }

    public void testAbstractSuperclassInjectionResolves() {
        addClass("package demo;\npublic abstract class BaseHandler {}");
        addClass("package demo;\n@org.springframework.stereotype.Component\npublic class ConcreteHandler extends BaseHandler {}");
        consumer("@org.springframework.beans.factory.annotation.Autowired demo.BaseHandler handler;");
        assertEquals(List.of(), beanProblems("demo/Consumer.java"));
    }

    public void testTwoImplementationsAreAmbiguous() {
        addInterfaceWithImpls("", "");
        consumer("@org.springframework.beans.factory.annotation.Autowired demo.Greeter greeter;");
        assertEquals(List.of("More than one bean of type 'demo.Greeter': implA, implB"),
                beanProblems("demo/Consumer.java"));
    }

    public void testPrimaryClearsAmbiguity() {
        addInterfaceWithImpls("@org.springframework.context.annotation.Primary", "");
        consumer("@org.springframework.beans.factory.annotation.Autowired demo.Greeter greeter;");
        assertEquals(List.of(), beanProblems("demo/Consumer.java"));
    }

    public void testFallbackYieldsToOrdinaryBean() {
        addInterfaceWithImpls("@org.springframework.context.annotation.Fallback", "");
        consumer("@org.springframework.beans.factory.annotation.Autowired demo.Greeter greeter;");
        assertEquals(List.of(), beanProblems("demo/Consumer.java"));
    }

    public void testQualifierPicksOneImplementation() {
        addInterfaceWithImpls("", "");
        consumer("@org.springframework.beans.factory.annotation.Autowired\n"
                + "  @org.springframework.beans.factory.annotation.Qualifier(\"implB\") demo.Greeter greeter;");
        assertEquals(List.of(), beanProblems("demo/Consumer.java"));
    }

    public void testQualifierMatchingNothingIsAnError() {
        addInterfaceWithImpls("", "");
        consumer("@org.springframework.beans.factory.annotation.Autowired\n"
                + "  @org.springframework.beans.factory.annotation.Qualifier(\"nope\") demo.Greeter greeter;");
        assertEquals(List.of("No qualifying bean of type 'demo.Greeter' found"),
                beanProblems("demo/Consumer.java"));
    }

    public void testNamedWorksAsQualifierOnInjectionAndBean() {
        addInterfaceWithImpls("@jakarta.inject.Named(\"special\")", "");
        consumer("@org.springframework.beans.factory.annotation.Autowired\n"
                + "  @jakarta.inject.Named(\"special\") demo.Greeter greeter;");
        assertEquals(List.of(), beanProblems("demo/Consumer.java"));
        List<SpringBeanDef> pool = SpringContextService.getInstance(getProject()).allBeans();
        PsiField field = findClass("demo.Consumer").findFieldByName("greeter", false);
        var found = SpringBeanMatching.candidates(new SpringBeanMatching.InjectionSite(field, field.getType()), pool);
        assertEquals(List.of("implA"), found.stream().map(SpringBeanDef::displayName).toList());
    }

    public void testCustomQualifierAnnotationMatchesAnnotatedBean() {
        addClass("package demo;\n@org.springframework.beans.factory.annotation.Qualifier\npublic @interface Loud {}");
        addInterfaceWithImpls("@demo.Loud", "");
        consumer("@org.springframework.beans.factory.annotation.Autowired @demo.Loud demo.Greeter greeter;");
        assertEquals(List.of(), beanProblems("demo/Consumer.java"));
        List<SpringBeanDef> pool = SpringContextService.getInstance(getProject()).allBeans();
        PsiField field = findClass("demo.Consumer").findFieldByName("greeter", false);
        var found = SpringBeanMatching.candidates(new SpringBeanMatching.InjectionSite(field, field.getType()), pool);
        assertEquals(List.of("implA"), found.stream().map(SpringBeanDef::displayName).toList());
    }

    public void testResourceByFieldNamePicksTheNamedBean() {
        addInterfaceWithImpls("", "");
        consumer("@jakarta.annotation.Resource demo.Greeter implB;");
        assertEquals(List.of(), beanProblems("demo/Consumer.java"));
        List<SpringBeanDef> pool = SpringContextService.getInstance(getProject()).allBeans();
        PsiField field = findClass("demo.Consumer").findFieldByName("implB", false);
        var found = SpringBeanMatching.candidates(new SpringBeanMatching.InjectionSite(field, field.getType()), pool);
        assertEquals(List.of("implB"), found.stream().map(SpringBeanDef::displayName).toList());
    }

    public void testResourceExplicitNameWins() {
        addInterfaceWithImpls("", "");
        consumer("@jakarta.annotation.Resource(name = \"implA\") demo.Greeter other;");
        List<SpringBeanDef> pool = SpringContextService.getInstance(getProject()).allBeans();
        PsiField field = findClass("demo.Consumer").findFieldByName("other", false);
        var found = SpringBeanMatching.candidates(new SpringBeanMatching.InjectionSite(field, field.getType()), pool);
        assertEquals(List.of("implA"), found.stream().map(SpringBeanDef::displayName).toList());
    }

    public void testResourceWithUnknownFieldNameFallsBackToType() {
        addInterfaceWithImpls("");
        consumer("@jakarta.annotation.Resource demo.Greeter whatever;");
        assertEquals(List.of(), beanProblems("demo/Consumer.java"));
    }

    private void addBox() {
        addClass("package demo;\npublic class Box<T> {}");
        // the light fixture has no JDK; declare the collection type under its real FQN
        addClass("package java.util;\npublic interface List<E> {}");
    }

    public void testGenericBeanMethodSatisfiesInjectionOfThatType() {
        addBox();
        addClass("package demo;\n@org.springframework.context.annotation.Configuration\npublic class Cfg {\n"
                + "  @org.springframework.context.annotation.Bean\n"
                + "  public demo.Box<String> names() { return null; }\n"
                + "  @org.springframework.context.annotation.Bean\n"
                + "  public java.util.List<String> labels() { return null; }\n}");
        consumer("@org.springframework.beans.factory.annotation.Autowired demo.Box<String> names;\n"
                + "  @org.springframework.beans.factory.annotation.Autowired demo.Box raw;\n"
                + "  @org.springframework.beans.factory.annotation.Autowired java.util.List<String> labels;");
        assertEquals(List.of(), beanProblems("demo/Consumer.java"));
        List<SpringBeanDef> pool = SpringContextService.getInstance(getProject()).allBeans();
        PsiField namesField = findClass("demo.Consumer").findFieldByName("names", false);
        assertEquals(List.of("names"), SpringBeanMatching.candidates(
                        new SpringBeanMatching.InjectionSite(namesField, namesField.getType()), pool)
                .stream().map(SpringBeanDef::displayName).toList());
        // a List<String> bean is found for a List<String> site (Spring's collection-bean fallback)
        PsiField listField = findClass("demo.Consumer").findFieldByName("labels", false);
        assertEquals(List.of("labels"), SpringBeanMatching.candidates(listField.getType(), List.of(), pool)
                .stream().map(SpringBeanDef::displayName).toList());
    }

    public void testGenericParameterMismatchPrefersExactButNeverErrors() {
        addBox();
        addClass("package demo;\n@org.springframework.context.annotation.Configuration\npublic class Cfg {\n"
                + "  @org.springframework.context.annotation.Bean public demo.Box<String> a() { return null; }\n"
                + "  @org.springframework.context.annotation.Bean public demo.Box<Integer> b() { return null; }\n}");
        consumer("@org.springframework.beans.factory.annotation.Autowired demo.Box<String> s;");
        assertEquals(List.of(), beanProblems("demo/Consumer.java"));
    }

    public void testUnrelatedTypeStillReportsNoBean() {
        addInterfaceWithImpls("");
        addClass("package demo;\npublic class Stranger {}");
        consumer("@org.springframework.beans.factory.annotation.Autowired demo.Stranger s;");
        assertEquals(List.of("No qualifying bean of type 'demo.Stranger' found"),
                beanProblems("demo/Consumer.java"));
    }
}
