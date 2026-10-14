package dev.phucngu.intellaspring;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.phucngu.intellaspring.inspection.SpringAutowiringInspection;

import java.util.List;
import java.util.Objects;

/**
 * The autowiring analysis: unresolved injections are errors with a required=false quick-fix,
 * ambiguous ones are errors with per-candidate @Qualifier quick-fixes; @Primary and
 * @Qualifier narrow candidates; collection injections are skipped.
 */
public class SpringAutowiringInspectionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new SpringAutowiringInspection());
        addClass("package org.springframework.stereotype;\npublic @interface Component { String value() default \"\"; }");
        addClass("package org.springframework.context.annotation;\npublic @interface Configuration { }");
        addClass("package org.springframework.context.annotation;\npublic @interface Primary { }");
        addClass("package org.springframework.context.annotation;\npublic @interface Bean { String[] value() default {}; }");
        addClass("package org.springframework.beans.factory.annotation;\npublic @interface Autowired { boolean required() default true; }");
        addClass("package org.springframework.beans.factory.annotation;\npublic @interface Qualifier { String value(); }");
        addClass("package demo;\npublic class ReportService {}");
        addClass("package demo;\npublic class MissingService {}");
        addClass("package demo;\n@org.springframework.stereotype.Component\npublic class UserService {}");
        addClass("package demo;\n@org.springframework.context.annotation.Configuration\npublic class ReportConfig {\n"
                + "  @org.springframework.context.annotation.Bean\n"
                + "  public demo.ReportService reportA() { return null; }\n"
                + "  @org.springframework.context.annotation.Bean\n"
                + "  public demo.ReportService reportB() { return null; }\n"
                + "}");
    }

    private void addClass(String source) {
        java.util.regex.Matcher pkg = java.util.regex.Pattern.compile("package\\s+([\\w.]+);").matcher(source);
        java.util.regex.Matcher type = java.util.regex.Pattern.compile("(?:class|interface|enum)\\s+(\\w+)").matcher(source);
        assertTrue(type.find());
        String dir = pkg.find() ? pkg.group(1).replace('.', '/') + "/" : "";
        myFixture.addFileToProject(dir + type.group(1) + ".java", source);
    }

    /** Highlights the given fixture file and returns our autowiring problem descriptions. */
    private List<String> beanProblems(String relativePath) {
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir(relativePath));
        return myFixture.doHighlighting().stream()
                .map(info -> info.getDescription())
                .filter(Objects::nonNull)
                .filter(d -> d.contains("bean of type"))
                .toList();
    }

    public void testResolvedInjectionHasNoProblems() {
        myFixture.addFileToProject("demo/Consumer.java", """
                package demo;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.stereotype.Component;
                @Component
                public class Consumer {
                    @Autowired private UserService userService;
                }
                """);
        assertEquals(List.of(), beanProblems("demo/Consumer.java"));
    }

    public void testUnresolvedInjectionIsAnError() {
        myFixture.addFileToProject("demo/Consumer.java", """
                package demo;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.stereotype.Component;
                @Component
                public class Consumer {
                    @Autowired private MissingService missing;
                }
                """);
        assertEquals(List.of("No qualifying bean of type 'demo.MissingService' found"),
                beanProblems("demo/Consumer.java"));
    }

    public void testRequiredFalseQuickFix() {
        myFixture.addFileToProject("demo/Consumer.java", """
                package demo;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.stereotype.Component;
                @Component
                public class Consumer {
                    @Autowired private MissingService missing;
                }
                """);
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("demo/Consumer.java"));
        myFixture.doHighlighting();
        var fix = myFixture.getAllQuickFixes("demo/Consumer.java").stream()
                .filter(a -> a.getText().equals("Set @Autowired(required = false)"))
                .findFirst().orElseThrow();
        myFixture.launchAction(fix);
        assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().contains("@Autowired(required = false)"));
    }

    public void testAmbiguousInjectionListsCandidates() {
        myFixture.addFileToProject("demo/Consumer.java", """
                package demo;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.stereotype.Component;
                @Component
                public class Consumer {
                    @Autowired private ReportService reports;
                }
                """);
        assertEquals(List.of("More than one bean of type 'demo.ReportService': reportA, reportB"),
                beanProblems("demo/Consumer.java"));
    }

    public void testQualifierQuickFixNarrowsToOne() {
        myFixture.addFileToProject("demo/Consumer.java", """
                package demo;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.stereotype.Component;
                @Component
                public class Consumer {
                    @Autowired private ReportService reports;
                }
                """);
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("demo/Consumer.java"));
        myFixture.doHighlighting();
        var fix = myFixture.getAllQuickFixes("demo/Consumer.java").stream()
                .filter(a -> a.getText().contains("reportB"))
                .findFirst().orElseThrow();
        myFixture.launchAction(fix);
        assertTrue(myFixture.getFile().getText(),
                myFixture.getFile().getText().contains("@org.springframework.beans.factory.annotation.Qualifier(\"reportB\")"));
    }

    public void testExplicitQualifierResolvesAmbiguity() {
        myFixture.addFileToProject("demo/Consumer.java", """
                package demo;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.beans.factory.annotation.Qualifier;
                import org.springframework.stereotype.Component;
                @Component
                public class Consumer {
                    @Autowired @Qualifier("reportA") private ReportService reports;
                }
                """);
        assertEquals(List.of(), beanProblems("demo/Consumer.java"));
    }

    public void testPrimaryResolvesAmbiguity() {
        addClass("package demo;\n@org.springframework.context.annotation.Configuration\npublic class PrimaryConfig {\n"
                + "  @org.springframework.context.annotation.Bean\n"
                + "  @org.springframework.context.annotation.Primary\n"
                + "  public demo.ReportService reportPrimary() { return null; }\n"
                + "}");
        myFixture.addFileToProject("demo/Consumer.java", """
                package demo;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.stereotype.Component;
                @Component
                public class Consumer {
                    @Autowired private ReportService reports;
                }
                """);
        assertEquals(List.of(), beanProblems("demo/Consumer.java"));
    }

    public void testCollectionInjectionIsSkipped() {
        myFixture.addFileToProject("demo/Consumer.java", """
                package demo;
                import java.util.List;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.stereotype.Component;
                @Component
                public class Consumer {
                    @Autowired private List<ReportService> reports;
                }
                """);
        assertEquals(List.of(), beanProblems("demo/Consumer.java"));
    }

    public void testBeanMethodParameterIsChecked() {
        myFixture.addFileToProject("demo/ConsumerConfig.java", """
                package demo;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                @Configuration
                public class ConsumerConfig {
                    @Bean
                    public String greeting(MissingService missing) { return "x"; }
                }
                """);
        assertEquals(List.of("No qualifying bean of type 'demo.MissingService' found"),
                beanProblems("demo/ConsumerConfig.java"));
    }
}
