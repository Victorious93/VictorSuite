package com.termux.victor.victorfiles

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import com.termux.victor.config.VsLinks
import com.termux.victor.victorfiles.VictorFilesPanel.LaunchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Regression tests for the Victorious File Manager hand-off. The technical contract (packages, action,
 * extra, preference file) must stay byte-for-byte compatible with File-Manager's manifest; only the
 * user-facing text and repository link are "Victorious File Manager" / File-Manager.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class VictorFilesPanelTest {
    private val app: Application get() = org.robolectric.RuntimeEnvironment.getApplication()

    private fun file(path: String) = File("../$path").let { if (it.exists()) it else File(path) }

    @Test
    fun legacyContractIsUnchanged() {
        assertEquals("ai.victorious.filemanager.action.PANEL", VictorFilesPanel.ACTION_PANEL)
        assertEquals("ai.victorious.filemanager.extra.OPEN_PATH", VictorFilesPanel.EXTRA_OPEN_PATH)
        assertEquals("https://github.com/Victorious93/File-Manager", VsLinks.FILE_MANAGER_REPO)
    }

    @Test
    fun startGuardedClassifiesEveryCrossAppFailure() {
        assertEquals(LaunchResult.STARTED, VictorFilesPanel.startGuarded(Runnable { }))
        assertEquals(LaunchResult.ACTIVITY_NOT_FOUND, VictorFilesPanel.startGuarded(Runnable { throw ActivityNotFoundException("x") }))
        assertEquals(LaunchResult.PERMISSION_DENIED, VictorFilesPanel.startGuarded(Runnable { throw SecurityException("x") }))
        assertEquals(LaunchResult.FAILED, VictorFilesPanel.startGuarded(Runnable { throw IllegalStateException("x") }))
    }

    @Test
    fun absentAppMeansInactiveAndNotAvailable() {
        assertNull(VictorFilesPanel.panelIntent(app))
        assertFalse(VictorFilesPanel.isActive(app))
        assertEquals(LaunchResult.NOT_AVAILABLE, VictorFilesPanel.launchApp(app))
    }

    @Test
    fun panelIntentPrefersReleaseThenDebugPackage() {
        val pm = shadowOf(app.packageManager)
        val debug = Intent(VictorFilesPanel.ACTION_PANEL).setPackage("ai.victorious.filemanager.debug")
        pm.addResolveInfoForIntent(debug, android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = "ai.victorious.filemanager.debug"; name = "ai.victorious.filemanager.panel.PanelActivity"
            }
        })
        assertEquals("ai.victorious.filemanager.debug", VictorFilesPanel.panelIntent(app)!!.`package`)
        val release = Intent(VictorFilesPanel.ACTION_PANEL).setPackage("ai.victorious.filemanager")
        pm.addResolveInfoForIntent(release, android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = "ai.victorious.filemanager"; name = "ai.victorious.filemanager.panel.PanelActivity"
            }
        })
        assertEquals("ai.victorious.filemanager", VictorFilesPanel.panelIntent(app)!!.`package`)
    }

    @Test
    fun launchAppFallsThroughAWorkingSiblingWhenOnePackageRefuses() {
        val pm = shadowOf(app.packageManager)
        pm.addActivityIfNotPresent(ComponentName("ai.victorious.filemanager", "ai.victorious.filemanager.MainActivity"))
        pm.addActivityIfNotPresent(ComponentName("ai.victorious.filemanager.debug", "ai.victorious.filemanager.MainActivity"))
        for (pkg in listOf("ai.victorious.filemanager", "ai.victorious.filemanager.debug")) {
            pm.addIntentFilterForActivity(
                ComponentName(pkg, "ai.victorious.filemanager.MainActivity"),
                android.content.IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            )
        }
        assertEquals(LaunchResult.STARTED, VictorFilesPanel.launchApp(app))
        assertNotNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun enabledPreferenceDefaultsOnAndPersistsUnderLegacyName() {
        assertTrue(VictorFilesPanel.isEnabled(app))
        VictorFilesPanel.setEnabled(app, false)
        assertFalse(VictorFilesPanel.isEnabled(app))
        assertFalse(app.getSharedPreferences("victorfiles_panel", 0).getBoolean("use_panel", true))
    }

    @Test
    fun userFacingStringsNoLongerSayVictorFiles() {
        for (path in listOf("app/src/main/res/values/strings.xml", "app/src/main/res/values-zh-rCN/strings.xml")) {
            val bad = Regex("""<string name="(vs_file_manager_not_installed|victorfiles_open|victorfiles_panel_title|victorfiles_panel_summary|victorious_file_manager_not_installed)"[^>]*>([^<]*)</string>""")
                .findAll(file(path).readText()).filter { it.groupValues[2].contains("VictorFiles") }.map { it.groupValues[1] }.toList()
            assertTrue("$path still says VictorFiles in $bad", bad.isEmpty())
        }
    }
}
