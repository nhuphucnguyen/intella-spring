package dev.phucngu.intellaspring.jpa;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Spring Data repository detection: an interface whose type hierarchy (with generics
 * substituted through every hop) reaches {@code org.springframework.data.repository.Repository}.
 * The domain entity is the first type argument at that point — so both
 * {@code extends Repository<Person, Long>} and {@code extends JpaRepository<Person, Long>}
 * resolve to {@code Person}.
 */
public final class JpaRepositories {

    public static final String REPOSITORY_FQN = "org.springframework.data.repository.Repository";

    private JpaRepositories() {
    }

    public static boolean isRepositoryInterface(@NotNull PsiClass candidate) {
        return !candidate.isAnnotationType() && candidate.isInterface() && repositoryType(candidate) != null;
    }

    /** The substituted {@code Repository<Domain, Id>} supertype, or null when not a repository. */
    public static @Nullable PsiClassType repositoryType(@NotNull PsiClass candidate) {
        PsiElementFactory factory = JavaPsiFacade.getInstance(candidate.getProject()).getElementFactory();
        return findRepository(factory.createType(candidate), new HashSet<>());
    }

    /** The entity class the repository is declared for, or null. */
    public static @Nullable PsiClass domainTypeOf(@NotNull PsiClass repository) {
        PsiClassType type = repositoryType(repository);
        if (type == null) {
            return null;
        }
        PsiType[] parameters = type.getParameters();
        if (parameters.length < 1 || !(parameters[0] instanceof PsiClassType domain)) {
            return null;
        }
        return domain.resolve();
    }

    private static @Nullable PsiClassType findRepository(@Nullable PsiClassType current,
                                                         @NotNull Set<PsiClass> seen) {
        if (current == null) {
            return null;
        }
        PsiClass resolved = current.resolve();
        if (resolved == null || !seen.add(resolved)) {
            return null;
        }
        if (REPOSITORY_FQN.equals(resolved.getQualifiedName())) {
            return current;
        }
        PsiSubstitutor substitutor = current.resolveGenerics().getSubstitutor();
        for (PsiClassType supertype : resolved.getSuperTypes()) {
            PsiType substituted = substitutor.substitute(supertype);
            if (substituted instanceof PsiClassType substitutedType) {
                PsiClassType hit = findRepository(substitutedType, seen);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }
}
