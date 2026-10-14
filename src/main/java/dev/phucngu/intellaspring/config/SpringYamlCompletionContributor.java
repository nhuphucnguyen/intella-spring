package dev.phucngu.intellaspring.config;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import dev.phucngu.intellaspring.ui.IntellaSpringIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Completes configuration keys in application*.yml: aware of nesting — under
 * {@code spring: datasource:} it offers the next segment of matching metadata keys
 * ("url", "username", …), not the whole dotted name.
 */
public class SpringYamlCompletionContributor extends CompletionContributor {

    public SpringYamlCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters parameters,
                                          @NotNull com.intellij.util.ProcessingContext context,
                                          @NotNull CompletionResultSet result) {
                PsiElement position = parameters.getPosition();
                if (!SpringPropertiesCompletionContributor.isApplicationFile(
                        position.getContainingFile().getName())) {
                    return;
                }
                YAMLKeyValue entry = PsiTreeUtil.getParentOfType(position, YAMLKeyValue.class, false);
                if (entry == null || entry.getKey() == null) {
                    return;
                }
                int offset = parameters.getOffset();
                if (!entry.getKey().getTextRange().contains(offset) && !entry.getKey().getText().isEmpty()) {
                    // caret is in the value or a nested document, not this key
                }
                String typedKey = textBeforeCaret(entry.getKey().getTextRange(), offset,
                        parameters.getEditor().getDocument().getCharsSequence());
                if (typedKey == null) {
                    return;
                }
                List<String> path = enclosingPath(entry);
                for (String segment : nextSegments(
                        SpringConfigMetadataService.getInstance(position.getProject()).keys(),
                        path, typedKey)) {
                    result.addElement(LookupElementBuilder.create(segment)
                            .withIcon(IntellaSpringIcons.BEAN)
                            .withTypeText("spring boot configuration", true));
                }
            }
        });
    }

    /** The key segments enclosing this entry, outermost first (e.g. [spring, datasource]). */
    public static @NotNull List<String> enclosingPath(@NotNull YAMLKeyValue entry) {
        List<String> path = new java.util.ArrayList<>();
        for (YAMLKeyValue parent = PsiTreeUtil.getParentOfType(entry, YAMLKeyValue.class, true);
             parent != null;
             parent = PsiTreeUtil.getParentOfType(parent, YAMLKeyValue.class, true)) {
            String key = parent.getKeyText();
            if (!key.isBlank()) {
                path.add(0, key);
            }
        }
        return path;
    }

    /** Segments that can follow the given path prefix, filtered by the typed partial segment. */
    public static @NotNull Set<String> nextSegments(@NotNull List<ConfigKey> keys, @NotNull List<String> path,
                                             @NotNull String typed) {
        String prefix = String.join(".", path);
        Set<String> segments = new LinkedHashSet<>();
        for (ConfigKey key : keys) {
            String name = key.name();
            if (prefix.isEmpty()) {
                if (name.regionMatches(true, 0, typed, 0, typed.length())) {
                    segments.add(name.split("\\.")[0]);
                }
            } else if (name.regionMatches(true, 0, prefix, 0, prefix.length())
                    && name.length() > prefix.length() + 1) {
                String rest = name.substring(prefix.length() + 1);
                String segment = rest.split("\\.")[0];
                if (segment.regionMatches(true, 0, typed, 0, typed.length())) {
                    segments.add(segment);
                }
            }
        }
        return segments;
    }

    private static @Nullable String textBeforeCaret(@NotNull TextRange keyRange, int offset,
                                                    @NotNull CharSequence document) {
        if (!keyRange.contains(offset)) {
            return null;
        }
        return document.subSequence(keyRange.getStartOffset(), offset).toString();
    }
}
