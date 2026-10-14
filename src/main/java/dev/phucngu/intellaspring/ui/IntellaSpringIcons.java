package dev.phucngu.intellaspring.ui;

import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/** Plugin icons. The leaf marks anything the model identifies as a Spring bean. */
public final class IntellaSpringIcons {
    public static final @NotNull Icon BEAN = IconLoader.getIcon("/icons/springBean.svg", IntellaSpringIcons.class);

    private IntellaSpringIcons() {
    }
}
