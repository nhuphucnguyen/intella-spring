package dev.phucngu.intellaspring;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.phucngu.intellaspring.boot.BootProfiles;

import java.util.List;

/**
 * Profile discovery for the run editor's profile picker: file-name profiles
 * (application-<p>.properties/.yml/.yaml) and {@code spring.profiles.active} values,
 * sorted and deduplicated.
 */
public class BootProfilesTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("application.properties",
                "spring.application.name=demo\nspring.profiles.active=local, cloud\n");
        myFixture.addFileToProject("config/application-dev.properties", "x=1\n");
        myFixture.addFileToProject("application-prod.yml", "x: 1\n");
        myFixture.addFileToProject("src/application-tenant2.yaml", "x: 1\n");
        myFixture.addFileToProject("other.properties", "unrelated=true\n");
    }

    public void testDiscoversFileNameAndActivePropertyProfiles() {
        List<String> profiles = BootProfiles.discover(getProject());
        assertEquals(List.of("cloud", "dev", "local", "prod", "tenant2"), profiles);
    }

    public void testUnrelatedFilesDoNotAddProfiles() {
        myFixture.addFileToProject("src2/notes.txt", "nothing\n");
        List<String> profiles = BootProfiles.discover(getProject());
        assertTrue(profiles.containsAll(List.of("cloud", "local")));
        assertTrue(!profiles.contains("notes"));
        assertTrue(!profiles.contains("src2"));
    }
}
