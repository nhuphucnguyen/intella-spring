package dev.phucngu.intellaspring;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.phucngu.intellaspring.generate.GenerateBeanMethodAction;
import org.jetbrains.annotations.NotNull;

/**
 * Generate ▸ Spring @Bean Method…: appending a {@code @Bean} factory method to a
 * configuration class — default-constructible classes get {@code return new X();},
 * others get a {@code return null;} construction TODO, and name clashes get 2/3/…
 * suffixes.
 */
public class GenerateBeanMethodTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        addClass("package org.springframework.context.annotation;\npublic @interface Bean { String[] value() default {}; }");
        addClass("package org.springframework.context.annotation;\npublic @interface Configuration { }");
        addClass("package demo;\npublic class PlainThing { }");
        addClass("package demo;\npublic class WiredThing {\n"
                + "  public WiredThing(int seed) { }\n}");
        myFixture.addFileToProject("demo/AppConfig.java", """
                package demo;
                import org.springframework.context.annotation.Configuration;
                @Configuration
                public class AppConfig {
                    public int plainThing() { return 1; }
                }
                """);
    }

    public void testGeneratesNewCallForDefaultConstructible() {
        PsiMethod method = generate("demo/AppConfig.java", "demo.PlainThing");
        assertNotNull(method);
        String text = flattened(method);
        assertTrue("@Bean annotation:\n" + text, text.contains("@Bean"));
        assertTrue("factory method name:\n" + text, text.contains("public PlainThing plainThing2()"));
        assertTrue("no-arg construction:\n" + text, text.contains("return new PlainThing();"));
    }

    public void testGeneratesNullTodoWhenNotDefaultConstructible() {
        PsiMethod method = generate("demo/AppConfig.java", "demo.WiredThing");
        assertNotNull(method);
        String text = flattened(method);
        assertTrue("construction TODO:\n" + text,
                text.contains("return null; // TODO construct WiredThing"));
    }

    private @org.jetbrains.annotations.Nullable PsiMethod generate(@NotNull String configPath, @NotNull String beanFqn) {
        PsiFile configFile = PsiManager.getInstance(getProject()).findFile(myFixture.findFileInTempDir(configPath));
        assertNotNull(configFile);
        PsiClass config = PsiTreeUtil.findChildrenOfType(configFile, PsiClass.class).stream()
                .filter(c -> "AppConfig".equals(c.getName()))
                .findFirst()
                .orElseThrow();
        PsiClass bean = JavaPsiFacadeFind.findClass(getProject(), beanFqn);
        PsiMethod[] created = new PsiMethod[1];
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(), (Runnable) () ->
                created[0] = GenerateBeanMethodAction.insertBeanMethod(config, bean));
        return created[0];
    }

    private static @NotNull String flattened(@NotNull PsiMethod method) {
        return method.getText().replaceAll("\\s+", " ");
    }

    private static final class JavaPsiFacadeFind {
        static PsiClass findClass(@NotNull com.intellij.openapi.project.Project project, @NotNull String fqn) {
            PsiClass cls = com.intellij.psi.JavaPsiFacade.getInstance(project)
                    .findClass(fqn, com.intellij.psi.search.GlobalSearchScope.projectScope(project));
            assertNotNull("fixture class not found: " + fqn, cls);
            return cls;
        }
    }

    private void addClass(String source) {
        java.util.regex.Matcher pkg = java.util.regex.Pattern.compile("package\\s+([\\w.]+);").matcher(source);
        java.util.regex.Matcher type = java.util.regex.Pattern.compile("(?:class|interface|enum)\\s+(\\w+)").matcher(source);
        assertTrue(type.find());
        String dir = pkg.find() ? pkg.group(1).replace('.', '/') + "/" : "";
        myFixture.addFileToProject(dir + type.group(1) + ".java", source);
    }
}
