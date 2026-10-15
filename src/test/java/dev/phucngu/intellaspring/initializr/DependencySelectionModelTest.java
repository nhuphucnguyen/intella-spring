package dev.phucngu.intellaspring.initializr;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The selection model behind the wizard's dependency checklist: filtering rebuilds the
 * visible rows but never loses a checked item, and the summary names what is selected.
 */
public class DependencySelectionModelTest extends BasePlatformTestCase {

    private final DependencySelectionModel model = new DependencySelectionModel();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        model.setCatalog(List.of(
                new InitializrModels.DependencyGroup("Web", List.of(
                        dep("web", "Spring Web"),
                        dep("webflux", "Spring Reactive Web"))),
                new InitializrModels.DependencyGroup("Data", List.of(
                        dep("data-jpa", "Spring Data JPA"),
                        dep("jdbc", "JDBC API"))),
                new InitializrModels.DependencyGroup("Core", List.of(
                        dep("lombok", "Lombok"),
                        dep("actuator", "Spring Boot Actuator")))));
    }

    /** The reported flow: filter → check one → clear filter → check another → BOTH retained. */
    public void testSelectionSurvivesFilterChanges() {
        // filter "jpa" → check data-jpa
        assertTrue(model.visibleIds("jpa").contains("data-jpa"));
        model.setChecked("data-jpa", true);
        // clear the filter (full list visible), check another one
        assertEquals(6, model.visibleIds("").size());
        model.setChecked("web", true);
        assertEquals(List.of("data-jpa", "web"), model.selectedIds());
        // re-filter ("data" shows data-jpa and jdbc), uncheck through the live-state merge
        List<String> visible = model.visibleIds("data");
        assertEquals(List.of("data-jpa", "jdbc"), visible);
        model.syncVisible(visible, id -> false); // every visible row live-unchecked
        assertEquals(List.of("web"), model.selectedIds());
    }

    public void testSyncVisiblePreservesHiddenSelections() {
        model.setChecked("web", true);
        model.setChecked("lombok", true);
        // only "web" row visible and live-unchecked; lombok hidden — must survive
        model.syncVisible(List.of("web"), id -> false);
        assertEquals(List.of("lombok"), model.selectedIds());
    }

    public void testCatalogChangeDropsVanishedSelections() {
        model.setChecked("web", true);
        model.setChecked("lombok", true);
        model.setCatalog(List.of(new InitializrModels.DependencyGroup("Web", List.of(dep("web", "Spring Web")))));
        assertEquals(List.of("web"), model.selectedIds());
    }

    public void testFilterMatchesLabelsAndIdsCaseInsensitively() {
        assertEquals(List.of("webflux"), model.visibleIds("reactive"));
        assertEquals(List.of("data-jpa"), model.visibleIds("JPA"));
        assertEquals(List.of("jdbc"), model.visibleIds("jdbc api"));
    }

    public void testSummaryNamesSelectionsThenCounts() {
        assertEquals("No dependencies selected", model.summary());
        model.setChecked("web", true);
        assertEquals("Spring Web", model.summary());
        model.setChecked("lombok", true);
        model.setChecked("actuator", true);
        assertEquals("Spring Web, Lombok, Spring Boot Actuator", model.summary());
        model.setChecked("jdbc", true);
        assertEquals("Spring Web, Lombok, Spring Boot Actuator +1 more", model.summary());
    }

    private static @NotNull InitializrModels.Dependency dep(@NotNull String id, @NotNull String name) {
        return new InitializrModels.Dependency(id, name, "");
    }
}
