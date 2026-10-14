package dev.phucngu.intellaspring;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.phucngu.intellaspring.inspection.SpringAmbiguousMappingInspection;
import dev.phucngu.intellaspring.mvc.RequestMappingModel;

import java.util.List;
import java.util.Objects;

/**
 * Request-mapping model (path joining with class-level prefixes, HTTP verbs from the
 * annotation kind or @RequestMapping(method=…)) and the ambiguous-mapping inspection.
 */
public class SpringMvcMappingTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new SpringAmbiguousMappingInspection());
        addClass("package org.springframework.web.bind.annotation;\npublic @interface RequestMapping { String[] value() default {}; String[] path() default {}; String[] method() default {}; }");
        addClass("package org.springframework.web.bind.annotation;\npublic @interface GetMapping { String[] value() default {}; }");
        addClass("package org.springframework.web.bind.annotation;\npublic @interface PostMapping { String[] value() default {}; }");
        addClass("package demo;\n@org.springframework.web.bind.annotation.RequestMapping(\"/api\")\npublic class ApiController {\n"
                + "  @org.springframework.web.bind.annotation.GetMapping(\"/users\")\n"
                + "  public String listUsers() { return \"\"; }\n"
                + "  @org.springframework.web.bind.annotation.PostMapping(\"/users\")\n"
                + "  public String createUser() { return \"\"; }\n"
                + "  @org.springframework.web.bind.annotation.RequestMapping(value = \"/health\", method = {\"org.springframework.web.bind.annotation.RequestMethod.GET\"})\n"
                + "  public String health() { return \"\"; }\n"
                + "}");
        addClass("package demo;\n@org.springframework.web.bind.annotation.RequestMapping(\"/api\")\npublic class ClashingController {\n"
                + "  @org.springframework.web.bind.annotation.GetMapping(\"/users\")\n"
                + "  public String clash() { return \"\"; }\n"
                + "}");
        addClass("package demo;\n@org.springframework.web.bind.annotation.RequestMapping(\"/other\")\npublic class CleanController {\n"
                + "  @org.springframework.web.bind.annotation.GetMapping(\"/users\")\n"
                + "  public String fine() { return \"\"; }\n"
                + "}");
    }

    private void addClass(String source) {
        java.util.regex.Matcher pkg = java.util.regex.Pattern.compile("package\\s+([\\w.]+);").matcher(source);
        java.util.regex.Matcher type = java.util.regex.Pattern.compile("(?:class|interface|enum)\\s+(\\w+)").matcher(source);
        assertTrue(type.find());
        String dir = pkg.find() ? pkg.group(1).replace('.', '/') + "/" : "";
        myFixture.addFileToProject(dir + type.group(1) + ".java", source);
    }

    public void testPathsJoinClassPrefixAndVerbsComeFromAnnotationKind() {
        var mappings = RequestMappingModel.collect(getModule());
        var paths = mappings.stream()
                .map(m -> String.join("/", m.httpMethods()) + " " + m.path())
                .sorted()
                .toList();
        assertTrue(paths.toString(), paths.contains("GET /api/users"));
        assertTrue(paths.toString(), paths.contains("POST /api/users"));
        assertTrue(paths.toString(), paths.contains("GET /api/health"));
        assertTrue(paths.toString(), paths.contains("GET /other/users"));
        // five handlers total, all with class prefixes joined
        assertEquals(5, mappings.size());
    }

    public void testAmbiguousMappingFlagsBothSides() {
        // the clash points both ways — each handler names the other
        assertEquals(List.of("Ambiguous mapping: GET /api/users is also mapped by ApiController.listUsers()"),
                highlight("demo/ClashingController.java"));
        assertEquals(List.of("Ambiguous mapping: GET /api/users is also mapped by ClashingController.clash()"),
                highlight("demo/ApiController.java"));
    }

    public void testDistinctPathsAndDistinctVerbsAreClean() {
        // /api/users GET and POST are different mappings; /other/users is a different path
        var apiProblems = highlight("demo/ApiController.java");
        assertTrue(apiProblems.toString(), apiProblems.stream().noneMatch(d -> d.contains("POST")));
        assertEquals(List.of(), highlight("demo/CleanController.java"));
    }

    private List<String> highlight(String relativePath) {
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir(relativePath));
        return myFixture.doHighlighting().stream()
                .map(h -> h.getDescription())
                .filter(Objects::nonNull)
                .filter(d -> d.contains("Ambiguous mapping"))
                .toList();
    }

    public void testPathJoining() {
        assertEquals("/api/users", RequestMappingModel.joinPaths("/api", "/users"));
        assertEquals("/users", RequestMappingModel.joinPaths("", "users"));
        assertEquals("/", RequestMappingModel.joinPaths("", ""));
    }
}
