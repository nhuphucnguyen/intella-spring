package dev.phucngu.intellaspring;

import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.phucngu.intellaspring.spring.SpringContextService;
import dev.phucngu.intellaspring.spring.model.BeanOrigin;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;

import java.util.List;

/** Context composition (no duplicates across modules), Spring Data repository beans, and cache stability. */
public class SpringBeanContextTest extends BasePlatformTestCase {

    private void addClass(String source) {
        add("", source);
    }

    private PsiFile add(String root, String source) {
        java.util.regex.Matcher pkg = java.util.regex.Pattern.compile("package\\s+([\\w.]+);").matcher(source);
        java.util.regex.Matcher type = java.util.regex.Pattern.compile("(?:class|interface|enum)\\s+(\\w+)").matcher(source);
        assertTrue(type.find());
        String dir = root + (pkg.find() ? pkg.group(1).replace('.', '/') + "/" : "");
        return myFixture.addFileToProject(dir + type.group(1) + ".java", source);
    }

    private void springApi() {
        addClass("package org.springframework.stereotype;\npublic @interface Component { String value() default \"\"; }");
    }

    private SpringContextService service() {
        return SpringContextService.getInstance(getProject());
    }

    private static List<String> names(List<SpringBeanDef> beans) {
        return beans.stream().map(SpringBeanDef::displayName).sorted().toList();
    }

    public void testSpringDataRepositoryIsBean() {
        springApi();
        addClass("package org.springframework.data.repository;\n@NoRepositoryBean public interface Repository<T, ID> {}");
        addClass("package org.springframework.data.repository;\npublic @interface NoRepositoryBean {}");
        addClass("package org.springframework.data.jpa.repository;\n@org.springframework.data.repository.NoRepositoryBean public interface JpaRepository<T, ID> extends org.springframework.data.repository.Repository<T, ID> {}");
        addClass("package demo;\npublic class Person {}");
        addClass("package demo;\npublic interface PersonRepository extends org.springframework.data.jpa.repository.JpaRepository<Person, Long> {}");
        addClass("package demo;\n@org.springframework.data.repository.NoRepositoryBean\npublic interface BaseRepository<T> extends org.springframework.data.jpa.repository.JpaRepository<T, Long> {}");
        addClass("package demo;\npublic interface PetRepository extends BaseRepository<Person> {}");
        addClass("package demo;\n@org.springframework.stereotype.Component\npublic class Holder {}");

        List<SpringBeanDef> beans = service().beans(getModule());
        assertEquals(List.of("holder", "personRepository", "petRepository"), names(beans));
        SpringBeanDef repo = beans.stream().filter(b -> "personRepository".equals(b.beanName())).findFirst().orElseThrow();
        assertEquals(BeanOrigin.SPRING_DATA_REPOSITORY, repo.origin());
        assertEquals("demo.PersonRepository", repo.beanTypeName());
    }

    public void testMethodBodyEditKeepsSnapshotNewComponentRebuilds() {
        springApi();
        PsiFile file = add("", "package demo;\n@org.springframework.stereotype.Component\npublic class Svc { void run() { int x = 1; } }");
        List<SpringBeanDef> before = service().beans(getModule());
        assertEquals(List.of("svc"), names(before));
        assertSame(before, service().beans(getModule()));

        // An open editor has the AST loaded; without it a document edit is not classified as in-code-block.
        com.intellij.psi.util.PsiTreeUtil.collectElements(file, e -> false);
        Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
        int offset = file.getText().indexOf("int x = 1;");
        myFixture.openFileInEditor(file.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(offset + 9);
        myFixture.type("2");
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertSame(before, service().beans(getModule()));

        addClass("package demo;\n@org.springframework.stereotype.Component\npublic class Extra {}");
        List<SpringBeanDef> after = service().beans(getModule());
        assertNotSame(before, after);
        assertEquals(List.of("extra", "svc"), names(after));
    }
}
