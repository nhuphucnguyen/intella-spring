package dev.phucngu.intellaspring.initializr;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Selection state for the wizard's dependency checklist, kept OUTSIDE the CheckBoxList so
 * filtering (which rebuilds the list) can never lose a checked item: filtering and
 * re-filtering only change which rows are visible; toggles land here via the list's
 * listener, and every rebuild re-applies this state to the visible rows.
 */
final class DependencySelectionModel {

    private final Map<String, String> labelsById = new LinkedHashMap<>();
    private final Set<String> selected = new LinkedHashSet<>();

    /** Replaces the catalog; selections for ids that disappear are dropped. */
    void setCatalog(@NotNull List<InitializrModels.DependencyGroup> groups) {
        labelsById.clear();
        for (InitializrModels.DependencyGroup group : groups) {
            for (InitializrModels.Dependency dep : group.dependencies()) {
                labelsById.put(dep.id(), dep.name() + " (" + group.name() + ")");
            }
        }
        selected.retainAll(labelsById.keySet());
    }

    /** The ids whose label or id matches the query — the rows the list should show. */
    @NotNull List<String> visibleIds(@NotNull String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<String> visible = new ArrayList<>();
        for (Map.Entry<String, String> entry : labelsById.entrySet()) {
            if (q.isEmpty() || entry.getValue().toLowerCase(Locale.ROOT).contains(q)
                    || entry.getKey().toLowerCase(Locale.ROOT).contains(q)) {
                visible.add(entry.getKey());
            }
        }
        return visible;
    }

    /**
     * Merges the live checkbox state of the currently VISIBLE rows into the model —
     * rows that are hidden can't have changed and keep their state. Called before a
     * rebuild so the list's own state never wins over the model.
     */
    void syncVisible(@NotNull List<String> visibleIds, @NotNull Predicate<String> isChecked) {
        for (String id : visibleIds) {
            if (isChecked.test(id)) {
                selected.add(id);
            } else {
                selected.remove(id);
            }
        }
    }

    void setChecked(@NotNull String id, boolean value) {
        if (!labelsById.containsKey(id)) {
            return;
        }
        if (value) {
            selected.add(id);
        } else {
            selected.remove(id);
        }
    }

    boolean isChecked(@NotNull String id) {
        return selected.contains(id);
    }

    @NotNull List<String> selectedIds() {
        return new ArrayList<>(selected);
    }

    @NotNull String labelOf(@NotNull String id) {
        return labelsById.getOrDefault(id, id);
    }

    /** "Spring Web, Lombok +2 more" — everything when it fits, a count when it doesn't. */
    @NotNull String summary() {
        if (selected.isEmpty()) {
            return "No dependencies selected";
        }
        List<String> names = new ArrayList<>();
        for (String id : selected) {
            names.add(labelOf(id).contains(" (") ? labelOf(id).substring(0, labelOf(id).indexOf(" (")) : labelOf(id));
        }
        StringBuilder text = new StringBuilder();
        int shown = Math.min(names.size(), 3);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(names.get(i));
        }
        if (names.size() > shown) {
            text.append(" +").append(names.size() - shown).append(" more");
        }
        return text.toString();
    }
}
