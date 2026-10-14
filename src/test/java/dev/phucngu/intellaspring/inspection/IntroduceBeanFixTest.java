package dev.phucngu.intellaspring.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * "No qualifying bean" gets a register-as-bean quick-fix when the type is a concrete
 * project class without a stereotype; applying it adds the stereotype (name-heuristic
 * Service/Repository/Component) and clears the problem.
 */
public class IntroduceBeanFixTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new SpringAutowiringInspection());
        addClass("package org.springframework.stereotype;\npublic @interface Component { String value() default \"\"; }");
        addClass("package org.springframework.stereotype;\npublic @interface Service { String value() default \"\"; }");
        addClass("package org.springframework.stereotype;\npublic @interface Repository { String value() default \"\"; }");
        addClass("package org.springframework.beans.factory.annotation;\npublic @interface Autowired { boolean required() default true; }");
        addClass("package demo;\npublic class MissingService {}");
        addClass("package demo;\npublic class PaymentService {}");
        addClass("package demo;\npublic class OrderRepository {}");
        addClass("package demo;\npublic class PlainThing {}");
        myFixture.addFileToProject("demo/Consumer.java", """
                package demo;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.stereotype.Component;
                @Component
                public class Consumer {
                    @Autowired private MissingService missing;
                }
                """);
    }

    public void testFixOfferedAppliesAndClearsProblem() {
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("demo/Consumer.java"));
        myFixture.doHighlighting();
        IntentionAction fix = myFixture.getAllQuickFixes().stream()
                .filter(action -> action.getText().contains("Register 'demo.MissingService'"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("register-as-bean quick-fix not offered"));
        assertEquals("Register 'demo.MissingService' as a Spring bean (@Service)", fix.getText());
        myFixture.launchAction(fix);

        PsiFile registered = PsiManager.getInstance(getProject())
                .findFile(myFixture.findFileInTempDir("demo/MissingService.java"));
        assertNotNull(registered);
        assertTrue("@Service added:\n" + registered.getText(),
                registered.getText().contains("@Service"));
        List<String> problems = myFixture.doHighlighting().stream()
                .map(info -> info.getDescription())
                .filter(description -> description != null && description.contains("bean of type"))
                .toList();
        assertEquals("problem cleared after registration", List.of(), problems);
    }

    public void testStereotypeHeuristics() {
        assertEquals(SpringAnnotationsFqn.SERVICE, IntroduceBeanFix.stereotypeFor(classNamed("PaymentService")));
        assertEquals(SpringAnnotationsFqn.REPOSITORY, IntroduceBeanFix.stereotypeFor(classNamed("OrderRepository")));
        assertEquals(SpringAnnotationsFqn.COMPONENT, IntroduceBeanFix.stereotypeFor(classNamed("PlainThing")));
    }

    public void testEligibilityGuards() {
        assertTrue(IntroduceBeanFix.eligible(classNamed("PlainThing")));
        assertFalse(IntroduceBeanFix.eligible(null));
    }

    private @NotNull PsiClass classNamed(@NotNull String name) {
        PsiFile file = PsiManager.getInstance(getProject())
                .findFile(myFixture.findFileInTempDir("demo/" + name + ".java"));
        assertNotNull("fixture file for " + name, file);
        PsiClass cls = PsiTreeUtil.findChildrenOfType(file, PsiClass.class).stream()
                .filter(c -> name.equals(c.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("class not found: " + name));
        return cls;
    }

    /** Fqn strings kept local so the test does not depend on main-class constants under test. */
    private static final class SpringAnnotationsFqn {
        static final String SERVICE = "org.springframework.stereotype.Service";
        static final String REPOSITORY = "org.springframework.stereotype.Repository";
        static final String COMPONENT = "org.springframework.stereotype.Component";
    }

    private void addClass(String source) {
        java.util.regex.Matcher pkg = java.util.regex.Pattern.compile("package\\s+([\\w.]+);").matcher(source);
        java.util.regex.Matcher type = java.util.regex.Pattern.compile("(?:class|interface|enum)\\s+(\\w+)").matcher(source);
        assertTrue(type.find());
        String dir = pkg.find() ? pkg.group(1).replace('.', '/') + "/" : "";
        myFixture.addFileToProject(dir + type.group(1) + ".java", source);
    }
}
