package dev.phucngu.intellaspring.config;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.editor.Document;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import dev.phucngu.intellaspring.ui.IntellaSpringIcons;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Completes configuration keys in .properties files: at a key position (no '=' yet on the
 * line), every metadata key matching the typed prefix is offered whole, with type/default as
 * tail text. Application*.properties files only.
 */
public class SpringPropertiesCompletionContributor extends CompletionContributor {

    public SpringPropertiesCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters parameters,
                                          @NotNull com.intellij.util.ProcessingContext context,
                                          @NotNull CompletionResultSet result) {
                PsiElement position = parameters.getPosition();
                if (!(position.getContainingFile() instanceof PropertiesFile)
                        || !isApplicationFile(position.getContainingFile().getName())) {
                    return;
                }
                String prefix = keyPrefixBeforeCaret(parameters);
                if (prefix == null) {
                    return;
                }
                for (ConfigKey key : SpringConfigMetadataService
                        .getInstance(position.getProject()).index().keys()) {
                    if (key.name().regionMatches(true, 0, prefix, 0, prefix.length())) {
                        result.addElement(element(key));
                    }
                }
            }
        });
    }

    static boolean isApplicationFile(@NotNull String fileName) {
        String lower = fileName.toLowerCase();
        return lower.startsWith("application") && (lower.endsWith(".properties") || lower.endsWith(".yml")
                || lower.endsWith(".yaml"));
    }

    /** The typed key prefix on the caret's line, or null when the caret is in a value/comment. */
    static String keyPrefixBeforeCaret(@NotNull CompletionParameters parameters) {
        Document document = parameters.getEditor().getDocument();
        int offset = parameters.getOffset();
        int line = document.getLineNumber(offset);
        int lineStart = document.getLineStartOffset(line);
        String before = document.getText(new com.intellij.openapi.util.TextRange(lineStart, offset));
        if (before.contains("=") || before.trim().startsWith("#") || before.contains("!")) {
            return null;
        }
        return before.trim();
    }

    private static LookupElement element(@NotNull ConfigKey key) {
        LookupElementBuilder builder = LookupElementBuilder.create(key.name())
                .withIcon(IntellaSpringIcons.BEAN)
                .withTypeText("spring boot configuration", true);
        List<String> tail = new ArrayList<>();
        if (key.type() != null) {
            tail.add(shorten(key.type()));
        }
        if (key.defaultValue() != null) {
            tail.add("default: " + key.defaultValue());
        }
        if (key.deprecated()) {
            builder = builder.withStrikeoutness(true);
        }
        return builder.withTailText(tail.isEmpty() ? "" : "  " + String.join(" ", tail), true);
    }

    private static String shorten(@NotNull String type) {
        return type.substring(type.lastIndexOf('.') + 1);
    }
}
