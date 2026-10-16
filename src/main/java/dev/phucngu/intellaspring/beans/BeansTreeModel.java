package dev.phucngu.intellaspring.beans;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import dev.phucngu.intellaspring.spring.SpringContextService;
import dev.phucngu.intellaspring.spring.model.BeanOrigin;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure rows of the Beans tree: module → bean, with the model definition attached. */
public final class BeansTreeModel {

    public record BeanRow(@NotNull String name, @Nullable String typeShort, @NotNull BeanOrigin origin,
                          @Nullable String stereotype, @NotNull SpringBeanDef def) {
    }

    public record ModuleRow(@NotNull String moduleName, @NotNull List<BeanRow> beans) {
    }

    private BeansTreeModel() {
    }

    /** Every Spring module's beans, name-sorted; must run inside a read action. */
    public static @NotNull List<ModuleRow> build(@NotNull Project project) {
        SpringContextService service = SpringContextService.getInstance(project);
        List<ModuleRow> rows = new ArrayList<>();
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            if (module.isDisposed() || !service.isSpringModule(module)) {
                continue;
            }
            // The context includes dependency modules' and library beans; list each bean under its own module only.
            List<BeanRow> beans = service.beans(module).stream()
                    .filter(def -> com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement(def.sourceElement()) == module)
                    .map(def -> new BeanRow(def.displayName(), typeShort(def.beanTypeName()),
                            def.origin(), def.stereotype(), def))
                    .sorted(Comparator.comparing(BeanRow::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            if (!beans.isEmpty()) {
                rows.add(new ModuleRow(module.getName(), beans));
            }
        }
        rows.sort(Comparator.comparing(ModuleRow::moduleName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private static @Nullable String typeShort(@Nullable String fqn) {
        return fqn == null ? null : fqn.substring(fqn.lastIndexOf('.') + 1);
    }
}
