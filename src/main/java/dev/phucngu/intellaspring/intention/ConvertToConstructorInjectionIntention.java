package dev.phucngu.intellaspring.intention;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import dev.phucngu.intellaspring.spring.SpringAnnotations;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Alt+Enter on an {@code @Autowired}/{@code @Inject} field: the field (and every sibling
 * injection field) becomes a {@code final} constructor parameter assigned in the
 * constructor — IDEA Ultimate's favorite Spring modernization, on the free tier.
 * {@code @Resource} (name-based) and {@code @Value} (configuration, not injection) fields
 * are deliberately left alone, as are Lombok-generated constructors and classes that
 * already annotate a constructor with {@code @Autowired}.
 */
public class ConvertToConstructorInjectionIntention extends PsiBasedModCommandAction<PsiField> {

    private static final List<String> FIELD_INJECTION_ANNOTATIONS = List.of(
            SpringAnnotations.AUTOWIRED, SpringAnnotations.INJECT_JAVAX, SpringAnnotations.INJECT_JAKARTA);

    public ConvertToConstructorInjectionIntention() {
        super(PsiField.class);
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Convert @Autowired fields to constructor injection";
    }

    @Override
    protected boolean isElementApplicable(@NotNull PsiField field, @NotNull ActionContext context) {
        PsiClass cls = field.getContainingClass();
        if (cls == null || cls.isEnum() || cls.isRecord() || cls.isInterface() || cls.isAnnotationType()) {
            return false;
        }
        if (cls.getAnnotation("lombok.RequiredArgsConstructor") != null) {
            return false;
        }
        if (!isMigratable(field)) {
            return false;
        }
        PsiMethod[] constructors = cls.getConstructors();
        if (constructors.length > 1) {
            return false;
        }
        return constructors.length == 0 || constructors[0].getAnnotation(SpringAnnotations.AUTOWIRED) == null;
    }

    @Override
    protected @NotNull Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiField element) {
        int count = migratableFields(element.getContainingClass()).size();
        String text = count <= 1
                ? "Convert '@Autowired' field to constructor injection"
                : "Convert " + count + " '@Autowired' fields to constructor injection";
        return Presentation.of(text);
    }

    @Override
    protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiField element) {
        PsiClass cls = element.getContainingClass();
        if (cls == null) {
            return ModCommand.nop();
        }
        return ModCommand.psiUpdate(cls, ConvertToConstructorInjectionIntention::convert);
    }

    private static void convert(@NotNull PsiClass cls) {
        List<PsiField> fields = migratableFields(cls);
        if (fields.isEmpty()) {
            return;
        }
        Project project = cls.getProject();
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        PsiMethod constructor = existingOrNewConstructor(cls, factory, fields);
        if (constructor == null || constructor.getBody() == null) {
            return;
        }
        for (PsiField field : fields) {
            PsiAnnotation injection = injectionAnnotationOf(field);
            if (injection == null) {
                continue;
            }
            String qualifier = qualifierTextOf(field);
            String parameterText = (qualifier == null ? "" : qualifier + " ")
                    + field.getType().getCanonicalText(true) + " " + field.getName();
            constructor.getParameterList().add(factory.createParameterFromText(parameterText, constructor));
            constructor.getBody().addBefore(
                    factory.createStatementFromText("this." + field.getName() + " = " + field.getName() + ";", constructor),
                    constructor.getBody().getRBrace());
            injection.delete();
            PsiModifierList modifiers = field.getModifierList();
            if (modifiers != null) {
                modifiers.setModifierProperty(PsiModifier.FINAL, true);
            }
        }
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(constructor);
        com.intellij.psi.codeStyle.CodeStyleManager.getInstance(project).reformat(constructor);
    }

    /** The single declared constructor to extend, or a fresh one placed after the last field. */
    private static @org.jetbrains.annotations.Nullable PsiMethod existingOrNewConstructor(
            @NotNull PsiClass cls, @NotNull PsiElementFactory factory, @NotNull List<PsiField> fields) {
        PsiMethod[] constructors = cls.getConstructors();
        if (constructors.length == 1) {
            return constructors[0];
        }
        if (constructors.length > 1) {
            return null;
        }
        StringBuilder text = new StringBuilder("public ").append(cls.getName()).append("() { }");
        PsiMethod ctor = factory.createMethodFromText(text.toString(), cls);
        PsiField lastField = Arrays.stream(cls.getFields()).reduce((first, second) -> second).orElse(fields.get(fields.size() - 1));
        return (PsiMethod) cls.addAfter(ctor, lastField);
    }

    private static boolean isMigratable(@NotNull PsiField field) {
        if (field.getInitializer() != null || field.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }
        return injectionAnnotationOf(field) != null;
    }

    private static @org.jetbrains.annotations.Nullable PsiAnnotation injectionAnnotationOf(@NotNull PsiField field) {
        PsiModifierList modifiers = field.getModifierList();
        if (modifiers == null) {
            return null;
        }
        for (String fqn : FIELD_INJECTION_ANNOTATIONS) {
            PsiAnnotation annotation = modifiers.findAnnotation(fqn);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }

    /** The field's {@code @Qualifier("…")} verbatim (qualified) so it travels to the parameter, or null. */
    private static @org.jetbrains.annotations.Nullable String qualifierTextOf(@NotNull PsiField field) {
        PsiAnnotation qualifier = field.getAnnotation(SpringAnnotations.QUALIFIER);
        if (qualifier == null) {
            return null;
        }
        String value = AnnotationUtil.getDeclaredStringAttributeValue(qualifier, "value");
        return "@" + SpringAnnotations.QUALIFIER + (value == null ? "" : "(\"" + value.replace("\"", "\\\"") + "\")");
    }

    private static @NotNull List<PsiField> migratableFields(@org.jetbrains.annotations.Nullable PsiClass cls) {
        if (cls == null) {
            return List.of();
        }
        List<PsiField> result = new ArrayList<>();
        for (PsiField field : cls.getFields()) {
            if (isMigratable(field)) {
                result.add(field);
            }
        }
        return result;
    }
}
