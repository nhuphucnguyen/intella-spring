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
}
