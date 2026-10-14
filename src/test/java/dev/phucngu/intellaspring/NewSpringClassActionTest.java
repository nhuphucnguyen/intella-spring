package dev.phucngu.intellaspring;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.phucngu.intellaspring.generate.NewSpringClassAction;
import org.jetbrains.annotations.NotNull;

/**
 * New ▸ Spring @Configuration/@Component Class: the class lands in the selected package
 * directory with the stereotype (shortened to an import) on the declaration.
 */
public class NewSpringClassActionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("org/springframework/context/annotation/Configuration.java",
                "package org.springframework.context.annotation;\npublic @interface Configuration { }");
        myFixture.addFileToProject("org/springframework/stereotype/Component.java",
                "package org.springframework.stereotype;\npublic @interface Component { }");
        myFixture.addFileToProject("demo/Placeholder.java", "package demo;\nclass Placeholder { }");
    }

    public void testCreatesConfigurationClass() {
        PsiFile created = createInSource("WebConfig", "org.springframework.context.annotation.Configuration");
        assertNotNull(created);
        String text = created.getText();
        assertTrue("package computed from directory:\n" + text, text.contains("package demo;"));
        assertTrue("import added:\n" + text, text.contains("import org.springframework.context.annotation.Configuration;"));
        assertTrue("stereotype on class:\n" + text, text.contains("@Configuration"));
        assertTrue("class declared:\n" + text, text.contains("public class WebConfig {"));
        assertTrue("no qualified annotation left:\n" + text, !text.contains("@org.springframework"));
    }

    public void testCreatesComponentClass() {
        PsiFile created = createInSource("UserFacade", "org.springframework.stereotype.Component");
        assertNotNull(created);
        assertTrue(created.getText().contains("import org.springframework.stereotype.Component;"));
        assertTrue(created.getText().contains("@Component"));
    }

    public void testRefusesOverwrite() {
        assertNotNull(createInSource("WebConfig", "org.springframework.context.annotation.Configuration"));
        assertNull("second creation with the same name is refused",
                createInSource("WebConfig", "org.springframework.context.annotation.Configuration"));
    }

    private @org.jetbrains.annotations.Nullable PsiFile createInSource(@NotNull String name, @NotNull String stereotypeFqn) {
        PsiDirectory directory = PsiManager.getInstance(getProject())
                .findDirectory(myFixture.findFileInTempDir("demo"));
        assertNotNull("demo package directory", directory);
        PsiFile[] created = new PsiFile[1];
        WriteCommandAction.runWriteCommandAction(getProject(), (Runnable) () ->
                created[0] = NewSpringClassAction.createSpringClass(directory, name, stereotypeFqn));
        return created[0];
    }
}
