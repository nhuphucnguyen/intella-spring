package dev.phucngu.intellaspring;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * The cheapest possible proof that the plugin descriptor loads: a bad plugin.xml (unknown
 * element, wrong depends) only surfaces once the IDE-level test infrastructure boots the plugin.
 */
public class PluginLoadSmokeTest extends BasePlatformTestCase {
    public void testPluginIsLoadedAndEnabled() {
        var plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(PluginId.getId("community.intellaspring"));
        assertNotNull("intella-spring plugin must load in the test IDE", plugin);
        assertTrue("intella-spring plugin must be enabled", plugin.isEnabled());
    }

    public void testGoToBeanActionIsRegistered() {
        assertNotNull("Go to Spring Bean action must be registered",
                com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction("IntellaSpring.GoToBean"));
    }
}
