package dev.phucngu.intellaspring.ui;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import dev.phucngu.intellaspring.endpoints.EndpointsPanel;
import dev.phucngu.intellaspring.mvc.RequestMappingModel;
import dev.phucngu.intellaspring.spring.SpringAnnotations;
import dev.phucngu.intellaspring.spring.model.AnnotationValues;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;

/**
 * The endpoint gutter icon on handler methods: a "view" marker that opens the Spring
 * Endpoints tool window with this handler selected — the cheap path from any controller
 * method to the whole endpoint landscape. Tooltip shows verb + full path (class prefix
 * joined), matching the tree.
 */
public class SpringMappingLineMarkerProvider implements LineMarkerProvider {

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element instanceof PsiIdentifier identifier)
                || !(identifier.getParent() instanceof PsiMethod method)
                || method.getNameIdentifier() != identifier) {
            return null;
        }
        String annotationFqn = mappingAnnotationOf(method);
        if (annotationFqn == null || MarkerSupport.springModuleOf(element) == null) {
            return null;
        }
        String label = endpointLabel(method, annotationFqn);
        return new LineMarkerInfo<>(
                identifier,
                identifier.getTextRange(),
                com.intellij.icons.AllIcons.Actions.Preview,
                ignored -> label,
                (event, elt) -> EndpointsPanel.revealEndpoint(elt.getProject(), method),
                GutterIconRenderer.Alignment.LEFT,
                () -> "intellaspring.endpoint." + label);
    }

    private static @Nullable String mappingAnnotationOf(@NotNull PsiMethod method) {
        for (String fqn : SpringAnnotations.MAPPING_ANNOTATIONS.keySet()) {
            if (method.getAnnotation(fqn) != null) {
                return fqn;
            }
        }
        return null;
    }

    private static @NotNull String endpointLabel(@NotNull PsiMethod method, @NotNull String annotationFqn) {
        List<String> verbs = SpringAnnotations.MAPPING_ANNOTATIONS.get(annotationFqn);
        if (verbs.isEmpty()) {
            verbs = AnnotationValues.stringValues(method.getAnnotation(annotationFqn), "method").stream()
                    .map(v -> v.substring(v.lastIndexOf('.') + 1)).toList();
        }
        String verbLabel = verbs.isEmpty() ? "ANY" : String.join("+", verbs);
        List<String> paths = AnnotationValues.stringValues(method.getAnnotation(annotationFqn), "value");
        if (paths.isEmpty()) {
            paths = AnnotationValues.stringValues(method.getAnnotation(annotationFqn), "path");
        }
        String prefix = classPrefix(method.getContainingClass());
        return "Spring endpoint " + verbLabel + " " + RequestMappingModel.joinPaths(prefix,
                paths.isEmpty() ? "" : paths.get(0)) + " — click to show in Spring Endpoints";
    }

    private static @NotNull String classPrefix(@Nullable PsiClass containing) {
        if (containing == null) {
            return "";
        }
        PsiAnnotation requestMapping = containing.getAnnotation(SpringAnnotations.REQUEST_MAPPING);
        if (requestMapping == null) {
            return "";
        }
        List<String> paths = AnnotationValues.stringValues(requestMapping, "value");
        if (paths.isEmpty()) {
            paths = AnnotationValues.stringValues(requestMapping, "path");
        }
        return paths.isEmpty() ? "" : paths.get(0);
    }
}
