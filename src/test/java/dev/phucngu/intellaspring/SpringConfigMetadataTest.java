package dev.phucngu.intellaspring;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.phucngu.intellaspring.config.ConfigKey;
import dev.phucngu.intellaspring.config.SpringConfigMetadata;
import dev.phucngu.intellaspring.config.SpringConfigMetadataService;
import dev.phucngu.intellaspring.config.SpringUnknownConfigKeyInspection;
import dev.phucngu.intellaspring.config.SpringYamlCompletionContributor;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.List;
import java.util.Set;

/**
 * Configuration metadata: parsing the JSON format, discovery from module resources,
 * key completion in application.properties, next-segment completion in application.yml,
 * and the unknown-key inspection.
 */
public class SpringConfigMetadataTest extends BasePlatformTestCase {

    private static final String METADATA = """
            {
              "properties": [
                { "name": "server.port", "type": "java.lang.Integer", "defaultValue": 8080,
                  "description": "Server HTTP port." },
                { "name": "spring.datasource.url", "type": "java.lang.String" },
                { "name": "spring.datasource.username", "type": "java.lang.String" },
                { "name": "spring.jpa.show-sql", "type": "java.lang.Boolean", "defaultValue": false },
                { "name": "logging.level.root", "type": "java.lang.String" },
                { "name": "old.feature", "type": "java.lang.String",
                  "deprecation": { "reason": "replaced" } }
              ]
            }
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("META-INF/spring-configuration-metadata.json", METADATA);
    }

    public void testParserReadsNamesTypesDefaultsDeprecation() {
        List<ConfigKey> keys = SpringConfigMetadata.parse(METADATA);
        assertEquals(6, keys.size());
        ConfigKey port = keys.get(0);
        assertEquals("server.port", port.name());
        assertEquals("java.lang.Integer", port.type());
        assertEquals("8080", port.defaultValue());
        assertFalse(keys.get(5).deprecated() == false);
        assertTrue(keys.get(5).deprecated());
        assertEquals("spring", keys.get(1).firstSegment());
    }

    public void testMetadataDiscoveredFromModuleResources() {
        List<ConfigKey> keys = SpringConfigMetadataService.getInstance(getProject()).keys();
        assertTrue(keys.stream().anyMatch(k -> "server.port".equals(k.name())));
        assertTrue(keys.stream().anyMatch(k -> "spring.jpa.show-sql".equals(k.name())));
    }

    public void testPropertiesKeyCompletionOffersMatchingKeys() {
        myFixture.configureByText("application.properties", "spring.datasource.<caret>");
        myFixture.completeBasic();
        var items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertTrue(items.toString(), items.contains("spring.datasource.url"));
        assertTrue(items.toString(), items.contains("spring.datasource.username"));
        assertFalse(items.toString(), items.contains("server.port"));
    }

    public void testYamlCompletionOffersNextSegmentOnly() {
        var keys = SpringConfigMetadataService.getInstance(getProject()).keys();
        // under spring.datasource: with "us" typed, the only next segment is "username"
        assertEquals(Set.of("username"),
                SpringYamlCompletionContributor.nextSegments(keys, List.of("spring", "datasource"), "us"));
        // at top level, the first segments of every metadata key are offered
        assertEquals(Set.of("spring", "server", "logging", "old"),
                SpringYamlCompletionContributor.nextSegments(keys, List.of(), ""));
        // a full prefix keeps offering deeper segments
        assertEquals(Set.of("url", "username"),
                SpringYamlCompletionContributor.nextSegments(keys, List.of("spring", "datasource"), ""));
    }

    public void testUnknownKeyInspectionFlagsAndSpares() {
        myFixture.enableInspections(new SpringUnknownConfigKeyInspection());
        myFixture.addFileToProject("application.properties", """
                server.port=9090
                spring.datasource.nope=x
                my.custom.key=y
                """);
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("application.properties"));
        var problems = myFixture.doHighlighting().stream()
                .map(h -> h.getDescription())
                .filter(d -> d != null && d.contains("Unknown Spring Boot configuration key"))
                .toList();
        assertEquals(List.of("Unknown Spring Boot configuration key 'spring.datasource.nope'"), problems);
    }

    public void testDocumentationShowsTypeAndDefault() {
        myFixture.addFileToProject("application.properties", "server.port=9090");
        var file = myFixture.findFileInTempDir("application.properties");
        myFixture.configureFromExistingVirtualFile(file);
        var element = myFixture.getFile().findElementAt(
                myFixture.getFile().getText().indexOf("server.port"));
        var property = com.intellij.psi.util.PsiTreeUtil.getParentOfType(element,
                com.intellij.lang.properties.psi.Property.class);
        assertNotNull(property);
        String doc = new dev.phucngu.intellaspring.config.SpringConfigKeyDocumentationProvider()
                .generateDoc(property, property);
        assertNotNull(doc);
        assertTrue(doc, doc.contains("server.port"));
        assertTrue(doc, doc.contains("8080"));
    }

    private List<String> unknownKeyProblems(String fileName, String text) {
        myFixture.enableInspections(new SpringUnknownConfigKeyInspection());
        myFixture.addFileToProject(fileName, text);
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir(fileName));
        return myFixture.doHighlighting().stream()
                .map(h -> h.getDescription())
                .filter(d -> d != null && d.contains("Unknown Spring Boot configuration key"))
                .toList();
    }

    public void testMapTypedAndRelaxedKeysAreKnown() {
        myFixture.addFileToProject("META-INF/additional-spring-configuration-metadata.json", """
                {"properties": [
                  {"name": "logging.level", "type": "java.util.Map<java.lang.String,java.lang.String>"},
                  {"name": "spring.jpa.properties", "type": "java.util.Map<java.lang.String,java.lang.String>"},
                  {"name": "spring.datasource.driver-class-name", "type": "java.lang.String"},
                  {"name": "spring.servers", "type": "java.util.List<demo.Server>"}
                ]}
                """);
        assertEquals(List.of("Unknown Spring Boot configuration key 'spring.datasource.nope'"),
                unknownKeyProblems("application.properties", """
                        logging.level.com.example=DEBUG
                        spring.jpa.properties.hibernate.format_sql=true
                        spring.datasource.driverClassName=x
                        spring.datasource.driver_class_name=x
                        spring.servers[0].host=h
                        spring.datasource.nope=x
                        """));
    }

    public void testSnapshotReusedWithoutVfsChangeAndRefreshedOnMetadataEdit() {
        var service = SpringConfigMetadataService.getInstance(getProject());
        var index = service.index();
        int parsed = SpringConfigMetadataService.parseCount();
        for (int i = 0; i < 100; i++) {
            assertSame(index, service.index());
        }
        assertEquals(parsed, SpringConfigMetadataService.parseCount());

        var file = myFixture.findFileInTempDir("META-INF/spring-configuration-metadata.json");
        var document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file);
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(), () ->
                document.setText("{\"properties\": [{\"name\": \"fresh.key\", \"type\": \"java.lang.String\"}]}"));
        var refreshed = service.index();
        assertNotSame(index, refreshed);
        assertNotNull(refreshed.find("fresh.key"));
    }

    public void testLegacyDeprecatedFlag() {
        List<ConfigKey> keys = SpringConfigMetadata.parse(
                "{\"properties\": [{\"name\": \"a.b\", \"deprecated\": true}, {\"name\": \"a.c\"}]}");
        assertTrue(keys.get(0).deprecated());
        assertFalse(keys.get(1).deprecated());
    }

    public void testProjectKeysFromCompilerOutputAreRead() throws Exception {
        com.intellij.openapi.vfs.VirtualFile output = com.intellij.openapi.application.WriteAction.compute(() -> {
            var dir = myFixture.getTempDirFixture().findOrCreateDir("out/classes");
            var model = com.intellij.openapi.roots.ModuleRootManager.getInstance(getModule()).getModifiableModel();
            var compiler = model.getModuleExtension(com.intellij.openapi.roots.CompilerModuleExtension.class);
            compiler.inheritCompilerOutputPath(false);
            compiler.setCompilerOutputPath(dir);
            model.commit();
            return dir;
        });
        com.intellij.openapi.application.WriteAction.run(() -> {
            try {
                var meta = output.createChildDirectory(this, "META-INF");
                var file = meta.createChildData(this, "spring-configuration-metadata.json");
                com.intellij.openapi.vfs.VfsUtil.saveText(file,
                        "{\"properties\": [{\"name\": \"my.app.timeout\", \"type\": \"java.lang.Integer\"}]}");
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertTrue(SpringConfigMetadataService.getInstance(getProject()).keys().stream()
                .anyMatch(k -> "my.app.timeout".equals(k.name())));
    }

    public void testLibraryMetadataParsedOnceAcrossPsiEdits() throws Exception {
        java.nio.file.Path jar = java.nio.file.Files.createTempFile("starter", ".jar");
        try (var out = new java.util.jar.JarOutputStream(java.nio.file.Files.newOutputStream(jar))) {
            out.putNextEntry(new java.util.zip.ZipEntry("META-INF/spring-configuration-metadata.json"));
            out.write("{\"properties\": [{\"name\": \"lib.setting\", \"type\": \"java.lang.String\"}]}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
        }
        String url = "jar://" + jar.toString().replace('\\', '/') + "!/";
        com.intellij.testFramework.PsiTestUtil.addProjectLibrary(getModule(), "starter",
                com.intellij.openapi.vfs.VirtualFileManager.getInstance().refreshAndFindFileByUrl(url));
        myFixture.configureByText("Unrelated.java", "class Unrelated { }");
        var service = SpringConfigMetadataService.getInstance(getProject());
        List<ConfigKey> first = service.keys();
        assertTrue(first.stream().anyMatch(k -> "lib.setting".equals(k.name())));
        int parsed = SpringConfigMetadataService.parseCount();

        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(),
                () -> myFixture.getEditor().getDocument().insertString(0, "// edit\n"));
        com.intellij.psi.PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

        assertSame(first, service.keys());
        assertEquals(parsed, SpringConfigMetadataService.parseCount());

        service.invalidate(); // roots change: snapshot rebuilt, only the module-local file is re-read
        assertTrue(service.keys().stream().anyMatch(k -> "lib.setting".equals(k.name())));
        assertEquals(parsed + 1, SpringConfigMetadataService.parseCount());
    }
}
