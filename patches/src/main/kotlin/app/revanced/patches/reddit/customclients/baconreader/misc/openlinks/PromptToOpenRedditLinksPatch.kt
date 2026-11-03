package app.revanced.patches.reddit.customclients.baconreader.misc.openlinks

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.all.misc.resources.addResources
import app.revanced.patches.all.misc.resources.addResourcesPatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch
import app.revanced.patches.reddit.customclients.baconreader.misc.settings.PreferenceScreen
import app.revanced.patches.reddit.customclients.baconreader.misc.settings.settingsPatch
import app.revanced.patches.shared.misc.settings.preference.NonInteractivePreference

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/baconreader/openlinks/OpenLinksPatch;"

@Suppress("unused")
val promptToOpenRedditLinksPatch = bytecodePatch(
    name = "Prompt to open Reddit links",
    description = "Android does not allow the app to open Reddit links by itself. " +
        "Asks once to set them up, and adds an option to open the \"Open by default\" screen.",
) {
    dependsOn(sharedExtensionPatch, settingsPatch, addResourcesPatch)

    compatibleWith(
        "com.onelouder.baconreader"("6.1.4"),
        "com.onelouder.baconreader.premium"("6.1.4"),
    )

    apply {
        addResources("baconreader", "misc.openlinks.promptToOpenRedditLinksPatch")

        PreferenceScreen.LINKS.addPreferences(
            NonInteractivePreference(
                key = "revanced_open_links",
                summaryKey = null,
                tag = "app.revanced.extension.baconreader.openlinks.OpenLinksPreference",
                selectable = true,
            ),
        )

        mainActivityOnResumeMethod.addInstruction(
            0,
            "invoke-static { p0 }, $EXTENSION_CLASS_DESCRIPTOR->onMainResume(Landroid/app/Activity;)V",
        )
    }
}
