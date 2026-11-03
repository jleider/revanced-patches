package app.revanced.patches.reddit.customclients.baconreader.misc.settings

import app.revanced.patcher.classDef
import app.revanced.patcher.extensions.ExternalLabel
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.resourcePatch
import app.revanced.patches.all.misc.resources.addResources
import app.revanced.patches.all.misc.resources.addResourcesPatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch
import app.revanced.patches.shared.misc.settings.preference.BasePreference
import app.revanced.patches.shared.misc.settings.preference.BasePreferenceScreen
import app.revanced.patches.shared.misc.settings.preference.NonInteractivePreference
import app.revanced.patches.shared.misc.settings.preference.PreferenceScreenPreference
import app.revanced.patches.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import app.revanced.patches.shared.misc.settings.settingsPatch
import app.revanced.patches.youtube.misc.settings.modifyActivityForSettingsInjection
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val ACTIVITY_HOOK_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/baconreader/settings/BaconReaderActivityHook;"

private val preferences = mutableSetOf<BasePreference>()

private val settingsResourcePatch = resourcePatch {
    dependsOn(settingsPatch(preferences = preferences))

    apply {
        // The settings layout has a placeholder toolbar from the support library, which BaconReader does not have.
        // Use the platform toolbar instead. It is replaced at runtime by the extension.
        val layoutFile = get("res/layout/revanced_settings_with_toolbar.xml")
        layoutFile.writeText(
            layoutFile.readText()
                .replace("<android.support.v7.widget.Toolbar", "<Toolbar")
                .replace("app:navigationIcon=", "android:navigationIcon=")
                .replace("app:title=", "android:title="),
        )
    }
}

/**
 * Adds a "ReVanced" entry at the end of BaconReader's settings menu, with the settings of the other patches.
 */
val settingsPatch = bytecodePatch(
    description = "Adds settings for ReVanced to BaconReader.",
) {
    dependsOn(
        sharedExtensionPatch,
        settingsResourcePatch,
        addResourcesPatch,
    )

    apply {
        addResources("baconreader", "misc.settings.settingsPatch")

        // Add an "About" preference to the top.
        preferences += NonInteractivePreference(
            key = "revanced_settings_baconreader_screen_0_about",
            summaryKey = null,
            tag = "app.revanced.extension.shared.settings.preference.ReVancedAboutPreference",
            selectable = true,
        )

        // The settings are shown in GoogleApiActivity, as adding an activity fails for root installations.
        modifyActivityForSettingsInjection(
            googleApiActivityOnCreateMethod.classDef,
            googleApiActivityOnCreateMethod,
            ACTIVITY_HOOK_CLASS_DESCRIPTOR,
            true,
        )

        // The settings menu shows the titles of the app's sections. Add the ReVanced entry to them.
        settingsMenuInitAdapterMethodMatch.method.apply {
            val sectionTitlesIndex = settingsMenuInitAdapterMethodMatch[0]
            val register = getInstruction<OneRegisterInstruction>(sectionTitlesIndex).registerA

            addInstructions(
                sectionTitlesIndex + 1,
                """
                    invoke-static { v$register }, $ACTIVITY_HOOK_CLASS_DESCRIPTOR->addSettingsMenuEntry([Ljava/lang/String;)[Ljava/lang/String;
                    move-result-object v$register
                """,
            )
        }

        // Clicking the ReVanced entry opens the ReVanced settings instead of a section of the app.
        settingsActivityOpenFragmentMethod.apply {
            addInstructionsWithLabels(
                0,
                """
                    invoke-static { p0, p1 }, $ACTIVITY_HOOK_CLASS_DESCRIPTOR->handleSettingsMenuClick(Landroid/app/Activity;I)Z
                    move-result v0
                    if-eqz v0, :original
                    return-void
                """,
                ExternalLabel("original", getInstruction(0)),
            )
        }
    }

    afterDependents {
        PreferenceScreen.close()
    }
}

object PreferenceScreen : BasePreferenceScreen() {
    val IMAGES = Screen(
        key = "revanced_settings_baconreader_screen_1_images",
        summaryKey = null,
        sorting = Sorting.UNSORTED,
    )
    val LINKS = Screen(
        key = "revanced_settings_baconreader_screen_2_links",
        summaryKey = null,
        sorting = Sorting.UNSORTED,
    )

    val COMMENTS = Screen(
        key = "revanced_settings_baconreader_screen_3_comments",
        summaryKey = null,
        sorting = Sorting.UNSORTED,
    )

    override fun commit(screen: PreferenceScreenPreference) {
        preferences += screen
    }
}
