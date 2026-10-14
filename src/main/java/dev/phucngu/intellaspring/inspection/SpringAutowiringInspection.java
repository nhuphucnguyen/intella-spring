package dev.phucngu.intellaspring.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import dev.phucngu.intellaspring.spring.SpringAnnotations;
import dev.phucngu.intellaspring.spring.SpringContextService;
import dev.phucngu.intellaspring.spring.model.AnnotationValues;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;

/**
 * The flagship: checks every injection point — {@code @Autowired} fields/setters/constructors
 * and the implicit dependencies of {@code @Bean} factory methods — against the bean model.
 * No candidate is an error (with a "set required = false" quick-fix); several non-primary
 * candidates are an ambiguity warning (with one add-@Qualifier quick-fix per candidate).
 * Collections, maps and Optionals inject many beans and are skipped, as are unresolvable types.
 */
public class SpringAutowiringInspection extends LocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            @Override
            public void visitField(@NotNull PsiField field) {
                if (dev.phucngu.intellaspring.spring.SpringInjections.isAutowiredField(field)) {
                    checkInjection(holder, field, field.getType(), field.getNameIdentifier());
                }
            }

            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                boolean wired = dev.phucngu.intellaspring.spring.SpringInjections.isAutowiredMethod(method);
                boolean beanFactory = dev.phucngu.intellaspring.spring.SpringInjections.isBeanFactoryMethod(method);
                boolean implicitConstructor = method.isConstructor()
                        && dev.phucngu.intellaspring.spring.SpringInjections.isImplicitInjectionConstructor(method);
                if (!wired && !beanFactory && !implicitConstructor) {
                    return;
                }
                for (PsiParameter parameter : method.getParameterList().getParameters()) {
                    if (parameter.getAnnotation(SpringAnnotations.VALUE) != null) {
                        continue; // configuration value, not a bean dependency
                    }
                    checkInjection(holder, parameter, parameter.getType(), parameter.getNameIdentifier());
                }
            }
        };
    }

    private void checkInjection(@NotNull ProblemsHolder holder, @NotNull PsiModifierListOwner member,
                                @NotNull PsiType type, PsiElement toMark) {
        if (toMark == null || !(type instanceof PsiClassType classType)) {
            return;
        }
        PsiClass injected = classType.resolve();
        if (injected == null || injected.getQualifiedName() == null
                || dev.phucngu.intellaspring.spring.SpringBeanMatching.MANY_BEAN_TYPES.contains(injected.getQualifiedName())) {
            return;
        }
        String fqn = injected.getQualifiedName();
        String qualifier = AnnotationValues.stringValue(member.getAnnotation(SpringAnnotations.QUALIFIER), "value");
        List<SpringBeanDef> candidates = dev.phucngu.intellaspring.spring.SpringBeanMatching.candidates(
                classType, qualifier, SpringContextService.getInstance(holder.getProject()).allBeans());
        if (candidates.isEmpty()) {
            if (isRequired(member)) {
                LocalQuickFix[] fixes = IntroduceBeanFix.eligible(injected)
                        ? new LocalQuickFix[]{new IntroduceBeanFix(injected), new SetRequiredFalseFix(member)}
                        : new LocalQuickFix[]{new SetRequiredFalseFix(member)};
                holder.registerProblem(toMark,
                        "No qualifying bean of type '" + fqn + "' found",
                        fixes);
            }
            return;
        }
        List<SpringBeanDef> primaries = candidates.stream().filter(SpringBeanDef::primary).toList();
        List<SpringBeanDef> effective = primaries.isEmpty() ? candidates : primaries;
        if (effective.size() > 1) {
            String names = effective.stream()
                    .sorted(Comparator.comparing(SpringBeanDef::displayName))
                    .map(SpringBeanDef::displayName)
                    .reduce((a, b) -> a + ", " + b).orElse("");
            LocalQuickFix[] fixes = effective.stream()
                    .sorted(Comparator.comparing(SpringBeanDef::displayName))
                    .map(bean -> (LocalQuickFix) new AddQualifierFix(member, bean.displayName()))
                    .toArray(LocalQuickFix[]::new);
            holder.registerProblem(toMark,
                    "More than one bean of type '" + fqn + "': " + names,
                    fixes);
        }
    }

    /** @Autowired defaults to required; explicit required = false opts the member out of errors. */
    private static boolean isRequired(@NotNull PsiModifierListOwner member) {
        PsiAnnotation autowired = member.getAnnotation(SpringAnnotations.AUTOWIRED);
        if (autowired == null) {
            // other injection kinds and implicit constructors/parameters are always required;
            // missing @Autowired annotation means "required" for them
            if (member instanceof PsiParameter parameter
                    && parameter.getDeclarationScope() instanceof PsiMethod declaring
                    && declaring.getAnnotation(SpringAnnotations.AUTOWIRED) != null) {
                autowired = declaring.getAnnotation(SpringAnnotations.AUTOWIRED);
            } else {
                return true;
            }
        }
        return !"false".equals(autowired.findDeclaredAttributeValue("required") != null
                ? autowired.findDeclaredAttributeValue("required").getText() : "true");
    }

    private static final class SetRequiredFalseFix implements LocalQuickFix {
        private final SmartPsiElementPointer<PsiModifierListOwner> member;

        SetRequiredFalseFix(@NotNull PsiModifierListOwner member) {
            this.member = SmartPointerManager.getInstance(member.getProject())
                    .createSmartPsiElementPointer(member);
        }

        @Override
        public @NotNull @IntentionFamilyName String getFamilyName() {
            return "Set @Autowired(required = false)";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiModifierListOwner target = member.getElement();
            if (target == null || target.getAnnotation(SpringAnnotations.AUTOWIRED) == null) {
                return;
            }
            PsiAnnotation autowired = target.getAnnotation(SpringAnnotations.AUTOWIRED);
            String fqn = autowired.getQualifiedName() != null ? autowired.getQualifiedName()
                    : SpringAnnotations.AUTOWIRED;
            PsiAnnotation replacement = JavaPsiFacade.getElementFactory(project)
                    .createAnnotationFromText("@" + fqn + "(required = false)", target);
            WriteCommandAction.runWriteCommandAction(project, getFamilyName(), null,
                    () -> autowired.replace(replacement));
        }
    }

    private static final class AddQualifierFix implements LocalQuickFix {
        private final SmartPsiElementPointer<PsiModifierListOwner> member;
        private final String beanName;

        AddQualifierFix(@NotNull PsiModifierListOwner member, @NotNull String beanName) {
            this.member = SmartPointerManager.getInstance(member.getProject())
                    .createSmartPsiElementPointer(member);
            this.beanName = beanName;
        }

        @Override
        public @NotNull @IntentionFamilyName String getFamilyName() {
            return "Add @Qualifier(\"" + beanName + "\")";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiModifierListOwner target = member.getElement();
            if (target == null || target.getModifierList() == null) {
                return;
            }
            PsiModifierList modifiers = target.getModifierList();
            PsiAnnotation existing = target.getAnnotation(SpringAnnotations.QUALIFIER);
            PsiAnnotation annotation = JavaPsiFacade.getElementFactory(project)
                    .createAnnotationFromText("@" + SpringAnnotations.QUALIFIER + "(\"" + beanName + "\")", target);
            WriteCommandAction.runWriteCommandAction(project, getFamilyName(), null, () -> {
                if (existing != null) {
                    existing.replace(annotation);
                } else {
                    modifiers.add(annotation);
                }
            });
        }
    }
}
