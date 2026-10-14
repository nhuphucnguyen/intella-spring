package dev.phucngu.intellaspring.spring.model;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import dev.phucngu.intellaspring.spring.SpringNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects {@code <bean>} declarations from Spring XML configs in the module. S1 walks raw
 * {@code XmlTag}s on purpose — the typed DOM model arrives with S3 and will reuse the same
 * bean shape. Config files are recognized by the Spring beans namespace, or — for DTD-era
 * and test configs — a {@code <beans>} root that actually contains bean declarations.
 */
public final class XmlBeanScanner {
    private XmlBeanScanner() {
    }

    public static @NotNull List<SpringBeanDef> scan(@NotNull Module module) {
        Project project = module.getProject();
        GlobalSearchScope moduleScope = GlobalSearchScope.moduleScope(module);
        GlobalSearchScope resolveScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
        List<SpringBeanDef> beans = new ArrayList<>();
        Map<String, SpringBeanDef> byName = new HashMap<>();
        List<String[]> pendingAliases = new ArrayList<>();
        for (VirtualFile file : com.intellij.psi.search.FilenameIndex.getAllFilesByExt(project, "xml", moduleScope)) {
            PsiFile psi = PsiManager.getInstance(project).findFile(file);
            if (!(psi instanceof XmlFile xml)) {
                continue;
            }
            XmlTag root = xml.getRootTag();
            if (root == null || !"beans".equals(root.getLocalName()) || !looksLikeSpring(root)) {
                continue;
            }
            collectBeans(root, null, resolveScope, beans, byName);
            for (XmlTag alias : root.findSubTags("alias")) {
                String name = alias.getAttributeValue("name");
                String aliasName = alias.getAttributeValue("alias");
                if (name != null && aliasName != null) {
                    pendingAliases.add(new String[]{name, aliasName});
                }
            }
        }
        for (String[] alias : pendingAliases) {
            SpringBeanDef target = byName.get(alias[0]);
            if (target != null && !target.aliases().contains(alias[1])) {
                List<String> aliases = new ArrayList<>(target.aliases());
                aliases.add(alias[1]);
                byName.put(alias[0], replaceAliases(beans, target, aliases));
            }
        }
        return beans;
    }

    private static boolean looksLikeSpring(@NotNull XmlTag root) {
        String namespace = root.getNamespace();
        if (namespace.startsWith("http://www.springframework.org/schema/")) {
            return true;
        }
        for (XmlTag bean : root.findSubTags("bean")) {
            if (bean.getAttributeValue("class") != null || bean.getAttributeValue("parent") != null) {
                return true;
            }
        }
        return false;
    }

    /** Recurses into nested {@code <beans profile="…">} groups; inline beans inside a bean are S3. */
    private static void collectBeans(@NotNull XmlTag tag, @Nullable List<String> enclosingProfiles,
                                     @NotNull GlobalSearchScope resolveScope,
                                     @NotNull List<SpringBeanDef> beans, @NotNull Map<String, SpringBeanDef> byName) {
        List<String> profiles = enclosingProfiles;
        String profileAttr = tag.getAttributeValue("profile");
        if (profileAttr != null) {
            profiles = SpringNames.splitNames(profileAttr);
        }
        for (XmlTag beanTag : tag.findSubTags("bean")) {
            SpringBeanDef bean = beanOf(beanTag, profiles, resolveScope);
            beans.add(bean);
            if (bean.beanName() != null) {
                byName.put(bean.beanName(), bean);
            }
        }
        for (XmlTag nested : tag.findSubTags("beans")) {
            collectBeans(nested, profiles, resolveScope, beans, byName);
        }
    }

    private static @NotNull SpringBeanDef beanOf(@NotNull XmlTag tag, @Nullable List<String> profiles,
                                                 @NotNull GlobalSearchScope resolveScope) {
        String id = tag.getAttributeValue("id");
        String nameAttr = tag.getAttributeValue("name");
        List<String> names = id == null ? SpringNames.splitNames(nameAttr != null ? nameAttr : "") : List.of();
        String name = id != null ? id : (!names.isEmpty() ? names.get(0) : null);
        List<String> aliases = names.size() > 1 ? List.copyOf(names.subList(1, names.size())) : List.of();
        String className = tag.getAttributeValue("class");
        PsiType type = resolveClass(tag.getProject(), className, resolveScope);
        String scope = tag.getAttributeValue("scope");
        return new SpringBeanDef(
                name,
                aliases,
                type,
                className,
                BeanOrigin.XML,
                null,
                List.of(),
                scope != null ? scope : SpringBeanDef.SINGLETON,
                profiles != null ? profiles : List.of(),
                "true".equals(tag.getAttributeValue("primary")),
                "true".equals(tag.getAttributeValue("lazy-init")),
                tag);
    }

    private static @Nullable PsiType resolveClass(@NotNull Project project, @Nullable String className,
                                                  @NotNull GlobalSearchScope scope) {
        if (className == null) {
            return null;
        }
        PsiClass resolved = JavaPsiFacade.getInstance(project).findClass(className, scope);
        return resolved != null ? JavaPsiFacade.getElementFactory(project).createType(resolved) : null;
    }

    private static @NotNull SpringBeanDef replaceAliases(@NotNull List<SpringBeanDef> beans,
                                                         @NotNull SpringBeanDef target, @NotNull List<String> aliases) {
        SpringBeanDef updated = new SpringBeanDef(target.beanName(), aliases, target.beanType(),
                target.beanTypeName(), target.origin(), target.stereotype(), target.qualifiers(),
                target.scope(), target.profiles(), target.primary(), target.lazy(), target.sourceElement());
        int index = beans.indexOf(target);
        if (index >= 0) {
            beans.set(index, updated);
        }
        return updated;
    }
}
