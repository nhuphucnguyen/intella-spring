package dev.phucngu.intellaspring;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.phucngu.intellaspring.inspection.SpringScheduledCronInspection;
import dev.phucngu.intellaspring.inspection.SpringValuePlaceholderInspection;
import dev.phucngu.intellaspring.schedule.CronExpressions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * {@code @Value} placeholder resolution against seeded configuration metadata, and
 * {@code @Scheduled(cron = …)} validation.
 */
public class SpringValueAndCronTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new SpringValuePlaceholderInspection(), new SpringScheduledCronInspection());
        addClass("package org.springframework.beans.factory.annotation;\npublic @interface Value { String value() default \"\"; }");
        addClass("package org.springframework.scheduling.annotation;\npublic @interface Scheduled {\n"
                + "  String cron() default \"\";\n  String zone() default \"\";\n}");
        addClass("package demo;\npublic class Jobs {\n"
                + "  @org.springframework.beans.factory.annotation.Value(\"${spring.datasource.url}\")\n"
                + "  private String url;\n"
                + "  @org.springframework.beans.factory.annotation.Value(\"${spring.datasource.nope}\")\n"
                + "  private String broken;\n"
                + "  @org.springframework.beans.factory.annotation.Value(\"${any.key:fallback}\")\n"
                + "  private String defaulted;\n"
                + "  @org.springframework.beans.factory.annotation.Value(\"plain string\")\n"
                + "  private String plain;\n"
                + "  @org.springframework.scheduling.annotation.Scheduled(cron = \"0 0 12 * * *\")\n"
                + "  public void noon() { }\n"
                + "  @org.springframework.scheduling.annotation.Scheduled(cron = \"0 61 12 * * *\")\n"
                + "  public void brokenMinute() { }\n"
                + "  @org.springframework.scheduling.annotation.Scheduled(cron = \"0 0 12 * * MON-FRI\", zone = \"UTC\")\n"
                + "  public void weekdays() { }\n"
                + "  @org.springframework.scheduling.annotation.Scheduled(cron = \"* * * * *\")\n"
                + "  public void fiveFields() { }\n"
                + "}");
        myFixture.addFileToProject("META-INF/spring-configuration-metadata.json", """
                {"properties": [
                  {"name": "spring.datasource.url", "type": "java.lang.String"},
                  {"name": "spring.datasource.username", "type": "java.lang.String"}
                ]}
                """);
    }

    private void addClass(String source) {
        java.util.regex.Matcher pkg = java.util.regex.Pattern.compile("package\\s+([\\w.]+);").matcher(source);
        java.util.regex.Matcher type = java.util.regex.Pattern.compile("(?:class|interface|enum)\\s+(\\w+)").matcher(source);
        assertTrue(type.find());
        String dir = pkg.find() ? pkg.group(1).replace('.', '/') + "/" : "";
        myFixture.addFileToProject(dir + type.group(1) + ".java", source);
    }

    private List<String> highlighting(String relativePath) {
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir(relativePath));
        return myFixture.doHighlighting().stream()
                .map(h -> h.getDescription())
                .filter(Objects::nonNull)
                .filter(d -> d.contains("cron") || d.contains("configuration key"))
                .sorted()
                .toList();
    }

    public void testValuePlaceholdersResolveAgainstMetadata() {
        assertEquals(List.of("Unresolved configuration key 'spring.datasource.nope'"),
                highlighting("demo/Jobs.java").stream()
                        .filter(d -> d.contains("configuration key")).toList());
    }

    public void testCronValidation() {
        List<String> cronProblems = highlighting("demo/Jobs.java").stream()
                .filter(d -> d.contains("cron")).toList();
        assertTrue(cronProblems.toString(),
                cronProblems.contains("Invalid cron expression '0 61 12 * * *': value '61' out of range 0-59"));
        assertTrue(cronProblems.toString(),
                cronProblems.contains("Invalid cron expression '* * * * *': expected 6 fields (second minute hour day-of-month month day-of-week), found 5"));
        assertEquals(2, cronProblems.size());
    }

    public void testCronAcceptances() {
        String[] valid = {"* * * * * *", "0 0 12 * * *", "0 */15 9-17 * * MON-FRI",
                "0 0 0 L * ?", "*/30 * * * * MON,SAT", "@daily", "-",
                "0 0 12 ? * 6#3", "0 0 12 L-3 * ?"}; // Spring cron is six fields — no year
        for (String expression : valid) {
            assertNull(expression, CronExpressions.describeProblem(expression));
        }
    }

    public void testCronRejections() {
        assertEquals("value '60' out of range 0-59", CronExpressions.describeProblem("60 * * * * *"));
        assertEquals("value '25' out of range 0-23", CronExpressions.describeProblem("0 0 25 * * *"));
        assertNotNull(CronExpressions.describeProblem("0 0 12 * FOO *"));
        assertEquals("inverted range 'FRI-MON'", CronExpressions.describeProblem("0 0 12 * * FRI-MON"));
        assertEquals("invalid step '/0'", CronExpressions.describeProblem("0/0 * * * * *"));
        assertNotNull(CronExpressions.describeProblem(""));
    }

    private int probes;

    private List<String> placeholderProblems(String valueExpression) {
        String name = "Probe" + probes++;
        addClass("package demo;\npublic class " + name + " {\n"
                + "  @org.springframework.beans.factory.annotation.Value(\"" + valueExpression + "\")\n"
                + "  private String field;\n}");
        return highlighting("demo/" + name + ".java").stream().filter(d -> d.contains("configuration key")).toList();
    }

    public void testKeyDefinedInApplicationPropertiesResolves() {
        myFixture.addFileToProject("application.properties", "app.greeting=hi\n");
        assertEquals(List.of(), placeholderProblems("${app.greeting}"));
    }

    public void testKeyDefinedInNestedYamlResolves() {
        myFixture.addFileToProject("application-dev.yml", "app:\n  greeting: hi\n  max-size: 3\n");
        assertEquals(List.of(), placeholderProblems("${app.greeting}"));
        assertEquals(List.of(), placeholderProblems("${app.maxSize}"));
    }

    public void testMissingKeyStillFlagged() {
        myFixture.addFileToProject("application.properties", "app.greeting=hi\n");
        assertEquals(List.of("Unresolved configuration key 'app.farewell'"), placeholderProblems("${app.farewell}"));
    }

    public void testEnvironmentStyleAndNestedPlaceholders() {
        assertEquals(List.of(), placeholderProblems("${HOME}/${JAVA_HOME}"));
        assertEquals(List.of(), placeholderProblems("${a.b:${c.d:x}}"));
        assertEquals(List.of("Unresolved configuration key 'c.d'"), placeholderProblems("${a.b:${c.d}}"));
        assertEquals(List.of(), placeholderProblems("${${x.y}:z}"));
        assertEquals(List.of(), placeholderProblems("${unbalanced"));
    }
}
