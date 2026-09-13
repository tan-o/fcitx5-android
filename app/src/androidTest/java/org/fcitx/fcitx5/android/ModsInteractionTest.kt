package org.fcitx.fcitx5.android

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import org.fcitx.fcitx5.android.data.rime.RimeManager
import org.fcitx.fcitx5.android.ui.main.MainActivity
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.junit.Assert.*
import org.junit.Test

class ModsInteractionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private fun open(route: SettingsRoute) = ActivityScenario.launch<MainActivity>(
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_RUN)
            .putExtra(MainActivity.EXTRA_SETTINGS_ROUTE, route)
    )
    private fun click(text: String) {
        val item = device.wait(Until.findObject(By.text(text)), 15_000)
        assertNotNull("Missing UI: $text", item)
        item.click()
        device.waitForIdle()
    }
    @Test fun cloneAndModelDialogsOpenWithTheApplicationTheme() {
        open(SettingsRoute.Rime).use {
            click("Clone 方案仓库")
            assertTrue(device.wait(Until.hasObject(By.text("Clone 公开仓库")), 5000))
            device.pressBack()
            click("为拼音方案启用／停用")
            assertTrue(device.wait(Until.hasObject(By.text("选择已有方案")), 5000))
            device.pressBack()
        }
    }
    @Test fun jgitCanCloneAPublicSchemeOnAndroid() = runBlocking {
        val repo = RimeManager.clone("https://github.com/rime/rime-prelude.git")
        try { assertTrue(repo.resolve("punctuation.yaml").isFile); RimeManager.update(repo) }
        finally { repo.deleteRecursively() }
    }
    @Test fun customYamlPreservesOtherPatches() = runBlocking {
        val name = "instrumentation_check.custom.yaml"
        try {
            RimeManager.saveCustom(name, "patch:\n  menu/page_size: 7\n")
            RimeManager.setModel("instrumentation_check", false)
            val saved = RimeManager.customFile(name).readText()
            assertTrue(saved.contains("menu/page_size"))
            assertTrue(saved.contains("grammar/language"))
        } finally { RimeManager.customFile(name).delete() }
    }
}
