package dev.phucngu.intellaspring;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import dev.phucngu.intellaspring.spring.SpringContextService;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;

import java.io.IOException;
import java.util.List;

/** A module depending on another must see the dependency's beans exactly once. */
public class SpringMultiModuleBeanTest extends HeavyPlatformTestCase {

    private Module addModuleWithSource(String name, String... files) throws IOException {
        VirtualFile root = getTempDir().createVirtualDir();
        Module module = createModuleAt(name, getProject(), com.intellij.openapi.module.EmptyModuleType.getInstance(),
                root.toNioPath());
        VirtualFile src = com.intellij.openapi.application.WriteAction.compute(() -> root.createChildDirectory(this, "src"));
        PsiTestUtil.addSourceRoot(module, src);
        for (int i = 0; i < files.length; i += 2) {
            String fileName = files[i];
            String text = files[i + 1];
            com.intellij.openapi.application.WriteAction.runAndWait(() -> {
                VirtualFile file = src.createChildData(this, fileName);
                VfsUtil.saveText(file, text);
            });
        }
        return module;
    }

    private static List<String> names(List<SpringBeanDef> beans) {
        return beans.stream().map(SpringBeanDef::displayName).sorted().toList();
    }

    public void testDependencyBeansAppearExactlyOnce() throws IOException {
        Module api = addModuleWithSource("api",
                "Component.java", "package org.springframework.stereotype; public @interface Component { String value() default \"\"; }",
                "Shared.java", "@org.springframework.stereotype.Component public class Shared {}");
        Module app = addModuleWithSource("app",
                "Own.java", "@org.springframework.stereotype.Component public class Own {}");
        ModuleRootModificationUtil.addDependency(app, api);

        SpringContextService service = SpringContextService.getInstance(getProject());
        System.out.println("DBG api=" + names(service.beans(api)) + " deps="
            + com.intellij.openapi.roots.ModuleRootManager.getInstance(app).getDependencyModuleNames().length);
        assertEquals(List.of("own", "shared"), names(service.beans(app)));
        assertEquals(List.of("shared"), names(service.beans(api)));
        assertEquals(List.of("own", "shared"), names(service.allBeans()));
    }
}
