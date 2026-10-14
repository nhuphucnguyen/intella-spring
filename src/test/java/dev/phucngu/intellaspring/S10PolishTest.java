package dev.phucngu.intellaspring;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.phucngu.intellaspring.boot.BootApplicationClasses;
import dev.phucngu.intellaspring.ui.SpringMappingLineMarkerProvider;

import java.util.Set;

/**
 * S10 polish pieces: the run editor's main-class picker finds every Boot application
 * (meta chain, main method required), and the mapping gutter marker carries the endpoint
 * label (verb + class-prefixed path).
 */
public class S10PolishTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        addClass("package org.springframework.stereotype;\npublic @interface Component { String value() default \"\"; }");
        addClass("package org.springframework.context.annotation;\npublic @interface Configuration { }");
        addClass("package org.springframework.boot;\npublic @interface SpringBootConfiguration { }");
        addClass("package org.springframework.boot.autoconfigure;\n@org.springframework.boot.SpringBootConfiguration\n@org.springframework.context.annotation.Configuration\npublic @interface SpringBootApplication { }");
        addClass("package org.springframework.web.bind.annotation;\npublic @interface RequestMapping { String[] value() default {}; String[] path() default {}; String[] method() default {}; }");
        addClass("package org.springframework.web.bind.annotation;\npublic @interface GetMapping { String[] value() default {}; }");
        addClass("package demo;\n@org.springframework.boot.autoconfigure.SpringBootApplication\npublic class FundNodeApplication {\n"
                + "  public static void main(String[] args) { }\n"
                + "  @org.springframework.web.bind.annotation.RequestMapping(\"/api\")\n"
                + "  public static class NestedController {\n"
                + "    @org.springframework.web.bind.annotation.GetMapping(\"/users\")\n"
                + "    public String listUsers() { return \"\"; }\n"
                + "  }\n"
                + "}");
        addClass("package demo;\n@org.springframework.boot.autoconfigure.SpringBootApplication\npublic class SecondApplication {\n"
                + "  public static void main(String[] args) { }\n"
                + "}");
        // annotated but no main method — must not appear in the picker
        addClass("package demo;\n@org.springframework.boot.autoconfigure.SpringBootApplication\npublic class NoMainApplication { }");
        addClass("package demo;\npublic class PlainService { }");
    }

    private void addClass(String source) {
        java.util.regex.Matcher pkg = java.util.regex.Pattern.compile("package\\s+([\\w.]+);").matcher(source);
        java.util.regex.Matcher type = java.util.regex.Pattern.compile("(?:class|interface|enum)\\s+(\\w+)").matcher(source);
        assertTrue(type.find());
        String dir = pkg.find() ? pkg.group(1).replace('.', '/') + "/" : "";
        myFixture.addFileToProject(dir + type.group(1) + ".java", source);
    }

    private PsiClass clazz(String fqn) {
        return com.intellij.psi.JavaPsiFacade.getInstance(getProject())
                .findClass(fqn, com.intellij.psi.search.GlobalSearchScope.moduleWithDependenciesScope(getModule()));
    }

    public void testPickerFindsBootApplicationClassesWithMainOnly() {
        Set<PsiClass> found = BootApplicationClasses.findBootApplicationClasses(getProject());
        assertEquals(Set.of("demo.FundNodeApplication", "demo.SecondApplication"),
                found.stream().map(PsiClass::getQualifiedName).collect(java.util.stream.Collectors.toSet()));
    }

    public void testMappingGutterCarriesEndpointLabel() {
        var provider = new SpringMappingLineMarkerProvider();
        PsiClass controller = clazz("demo.FundNodeApplication.NestedController");
        PsiMethod handler = controller.findMethodsByName("listUsers", false)[0];
        var marker = provider.getLineMarkerInfo(handler.getNameIdentifier());
        assertNotNull("handler method must carry the endpoint gutter icon", marker);
        assertEquals("Spring endpoint GET /api/users — click to show in Spring Endpoints",
                marker.createGutterRenderer().getTooltipText());
        assertNull("plain class has no endpoint marker", provider.getLineMarkerInfo(clazz("demo.PlainService").getNameIdentifier()));
    }
}
