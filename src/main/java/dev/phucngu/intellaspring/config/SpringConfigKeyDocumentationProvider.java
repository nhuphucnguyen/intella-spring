package dev.phucngu.intellaspring.config;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Quick documentation (F1/Ctrl+Q) for configuration keys in application*.properties: type,
 * default value, description and deprecation, straight from the metadata.
 */
public class SpringConfigKeyDocumentationProvider extends AbstractDocumentationProvider {

    @Override
    public @Nullable String generateDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
        if (!(element instanceof Property property)
                || !SpringPropertiesCompletionContributor.isApplicationFile(
                        element.getContainingFile().getName())) {
            return null;
        }
        String key = property.getKey();
        if (key == null) {
            return null;
        }
        ConfigKey match = SpringConfigMetadataService.getInstance(element.getProject()).keys().stream()
                .filter(candidate -> candidate.name().equals(key))
                .findFirst()
                .orElse(null);
        if (match == null) {
            return null;
        }
        StringBuilder html = new StringBuilder("<div style='width:500px'>");
        html.append("<b>").append(match.name()).append("</b>");
        if (match.deprecated()) {
            html.append(" <span style='color:#cc0000'>(deprecated)</span>");
        }
        if (match.type() != null) {
            html.append("<br/>Type: <code>").append(match.type()).append("</code>");
        }
        if (match.defaultValue() != null) {
            html.append("<br/>Default: <code>").append(match.defaultValue()).append("</code>");
        }
        if (match.description() != null) {
            html.append("<p>").append(match.description().replace("\n", "<br/>")).append("</p>");
        }
        html.append("</div>");
        return html.toString();
    }
}
