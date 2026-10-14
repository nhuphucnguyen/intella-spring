package dev.phucngu.intellaspring.schedule;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Spring cron validation: six fields (second minute hour day-of-month month day-of-week),
 * lists, ranges, steps, L/W/# day markers, month/day names, and the @-macros plus "-"
 * (disabled). Returns the reason string when invalid — the message the inspection shows.
 */
public final class CronExpressions {

    private static final List<String> MONTHS = List.of(
            "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC");
    private static final List<String> DAYS = List.of(
            "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT");
    private static final List<String> MACROS = List.of(
            "@yearly", "@annually", "@monthly", "@weekly", "@daily", "@hourly");

    private record FieldSpec(int min, int max, boolean names, boolean dayMarkers) {
    }

    private static final List<FieldSpec> FIELDS = List.of(
            new FieldSpec(0, 59, false, false),  // second
            new FieldSpec(0, 59, false, false),  // minute
            new FieldSpec(0, 23, false, false),  // hour
            new FieldSpec(1, 31, false, true),   // day of month
            new FieldSpec(1, 12, true, false),   // month
            new FieldSpec(1, 7, true, true));    // day of week

    private CronExpressions() {
    }

    /** null when the expression is valid; otherwise a short reason. */
    public static @Nullable String describeProblem(@NotNull String expression) {
        String trimmed = expression.trim();
        if (trimmed.isEmpty()) {
            return "empty expression";
        }
        if (trimmed.equals("-") || MACROS.contains(trimmed.toLowerCase(Locale.ROOT))) {
            return null;
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length != 6) {
            return "expected 6 fields (second minute hour day-of-month month day-of-week), found " + parts.length;
        }
        for (int i = 0; i < parts.length; i++) {
            String problem = checkField(parts[i], FIELDS.get(i), i);
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    private static @Nullable String checkField(@NotNull String field, @NotNull FieldSpec spec, int index) {
        for (String term : field.split(",")) {
            if (term.isBlank()) {
                return "empty item in field '" + field + "'";
            }
            String problem = checkTerm(term, spec);
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    private static @Nullable String checkTerm(@NotNull String term, @NotNull FieldSpec spec) {
        int slash = term.indexOf('/');
        String range = slash >= 0 ? term.substring(0, slash) : term;
        String step = slash >= 0 ? term.substring(slash + 1) : null;
        if (step != null && (!step.chars().allMatch(Character::isDigit) || step.isEmpty() || Integer.parseInt(step) == 0)) {
            return "invalid step '/" + step + "'";
        }
        if (range.equals("*") || range.equals("?")) {
            return null;
        }
        if (spec.dayMarkers() && (range.equals("L") || range.equals("LW") || range.equals("W"))) {
            return null;
        }
        // day-of-week "nL" / "n#n", day-of-month "L-n"
        if (spec.dayMarkers()) {
            if (range.matches("[0-7]L")) {
                return null;
            }
            if (range.matches("[1-7]#[1-5]")) {
                return null;
            }
            if (range.matches("L-[0-9]+")) {
                return null;
            }
            if (range.matches("[0-9]+W")) {
                return null;
            }
        }
        int dash = range.indexOf('-');
        if (dash >= 0) {
            String from = range.substring(0, dash);
            String to = range.substring(dash + 1);
            Integer fromValue = numberOrName(from, spec);
            Integer toValue = numberOrName(to, spec);
            if (fromValue == null || toValue == null) {
                return "invalid range '" + range + "'";
            }
            if (fromValue > toValue) {
                return "inverted range '" + range + "'";
            }
            return null;
        }
        Integer value = numberOrName(range, spec);
        if (value == null) {
            return "invalid value '" + range + "'";
        }
        if (value < spec.min() || value > spec.max()) {
            return "value '" + range + "' out of range " + spec.min() + "-" + spec.max();
        }
        return null;
    }

    private static @Nullable Integer numberOrName(@NotNull String value, @NotNull FieldSpec spec) {
        if (value.chars().allMatch(Character::isDigit) && !value.isEmpty()) {
            return Integer.parseInt(value);
        }
        if (spec.names()) {
            int month = MONTHS.indexOf(value.toUpperCase(Locale.ROOT));
            if (month >= 0) {
                return month + 1;
            }
            int day = DAYS.indexOf(value.toUpperCase(Locale.ROOT));
            if (day >= 0) {
                return day + 1;
            }
        }
        return null;
    }
}
