package dev.phucngu.intellaspring;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.phucngu.intellaspring.endpoints.EndpointsTreeModel;
import dev.phucngu.intellaspring.endpoints.EndpointsTreeModel.ControllerRow;
import dev.phucngu.intellaspring.endpoints.EndpointsTreeModel.EndpointRow;
import dev.phucngu.intellaspring.endpoints.EndpointsTreeModel.ModuleRow;

import java.util.List;

/**
 * The data layer behind the Endpoints tool window: grouping per controller, sorting,
 * curl composition, and server.port awareness.
 */
public class EndpointsTreeModelTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        addClass("package org.springframework.stereotype;\npublic @interface Component { String value() default \"\"; }");
        addClass("package org.springframework.web.bind.annotation;\npublic @interface RequestMapping { String[] value() default {}; String[] path() default {}; String[] method() default {}; }");
        addClass("package org.springframework.web.bind.annotation;\npublic @interface GetMapping { String[] value() default {}; }");
        addClass("package org.springframework.web.bind.annotation;\npublic @interface PostMapping { String[] value() default {}; }");
        addClass("package demo;\n@org.springframework.web.bind.annotation.RequestMapping(\"/api\")\npublic class ApiController {\n"
                + "  @org.springframework.web.bind.annotation.PostMapping(\"/users\")\n"
                + "  public String createUser() { return \"\"; }\n"
                + "  @org.springframework.web.bind.annotation.GetMapping(\"/users\")\n"
                + "  public String listUsers() { return \"\"; }\n"
                + "  @org.springframework.web.bind.annotation.GetMapping(\"/health\")\n"
                + "  public String health() { return \"\"; }\n"
                + "}");
        addClass("package demo;\n@org.springframework.web.bind.annotation.RequestMapping(\"/other\")\npublic class BaseController {\n"
                + "  @org.springframework.web.bind.annotation.GetMapping(\"/users\")\n"
                + "  public String fine() { return \"\"; }\n"
                + "}");
        myFixture.addFileToProject("application.properties", "server.port=9090\n");
    }

    private void addClass(String source) {
        java.util.regex.Matcher pkg = java.util.regex.Pattern.compile("package\\s+([\\w.]+);").matcher(source);
        java.util.regex.Matcher type = java.util.regex.Pattern.compile("(?:class|interface|enum)\\s+(\\w+)").matcher(source);
        assertTrue(type.find());
        String dir = pkg.find() ? pkg.group(1).replace('.', '/') + "/" : "";
        myFixture.addFileToProject(dir + type.group(1) + ".java", source);
    }

    public void testGroupsByControllerAndSorts() {
        List<ModuleRow> rows = EndpointsTreeModel.build(getProject());
        assertEquals(1, rows.size());
        ModuleRow module = rows.get(0);
        assertEquals(List.of("ApiController", "BaseController"),
                module.controllers().stream().map(ControllerRow::controllerName).toList());
        ControllerRow api = module.controllers().get(0);
        // path-sorted within the controller: /api/health, /api/users GET, /api/users POST
        assertEquals(List.of("/api/health", "/api/users", "/api/users"),
                api.endpoints().stream().map(EndpointRow::path).toList());
        assertEquals("GET", api.endpoints().get(1).verbsLabel());
        assertEquals("POST", api.endpoints().get(2).verbsLabel());
    }

    public void testFlatModeSortsByPathAcrossControllers() {
        ModuleRow module = EndpointsTreeModel.build(getProject()).get(0);
        assertEquals(List.of("/api/health", "/api/users", "/api/users", "/other/users"),
                module.flatEndpoints().stream().map(EndpointRow::path).toList());
    }

    public void testCurlUsesVerbAndPort() {
        EndpointRow row = EndpointsTreeModel.build(getProject()).get(0).controllers().get(0).endpoints().get(2);
        assertEquals("curl -X POST 'http://localhost:9090/api/users'", EndpointsTreeModel.curlCommand(row, 9090));
        assertEquals("curl -X POST 'http://localhost:8080/api/users'", EndpointsTreeModel.curlCommand(row, 8080));
    }

    public void testServerPortReadFromApplicationProperties() {
        assertEquals(9090, EndpointsTreeModel.serverPort(getProject()));
    }

    public void testServerPortDefaultsTo8080() throws java.io.IOException {
        com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction(() -> {
            try {
                myFixture.findFileInTempDir("application.properties").delete(null);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals(8080, EndpointsTreeModel.serverPort(getProject()));
    }

    public void testVerbLabelAnyAndMulti() {
        assertEquals("ANY", EndpointsTreeModel.verbLabel(List.of()));
        assertEquals("GET+POST", EndpointsTreeModel.verbLabel(List.of("GET", "POST")));
    }
}
