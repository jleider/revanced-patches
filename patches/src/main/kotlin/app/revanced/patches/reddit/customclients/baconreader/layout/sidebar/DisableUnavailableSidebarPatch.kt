package app.revanced.patches.reddit.customclients.baconreader.layout.sidebar

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/baconreader/sidebar/DisableUnavailableSidebarPatch;"

@Suppress("unused")
val disableUnavailableSidebarPatch = bytecodePatch(
    name = "Disable unavailable sidebar",
    description = "Disables \"View Sidebar\" on Front Page, All, Popular, Friends and multireddits, " +
        "which have no sidebar.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.onelouder.baconreader",
        "com.onelouder.baconreader.premium",
    )

    apply {
        mainActivityOnPrepareOptionsMenuMethod.addInstructions(
            0,
            """
                iget-object v0, p0, $MAIN_ACTIVITY_CLASS_DESCRIPTOR->linksetKey:Lcom/onelouder/baconreader/data/LinksetKey;
                invoke-static { p1, v0 }, $EXTENSION_CLASS_DESCRIPTOR->updateSidebarItem(Landroid/view/Menu;Ljava/lang/Object;)V
            """,
        )

        listOf(mainActivityOpenLinksetMethod, mainActivityOnFirstLinkMethod).forEach { method ->
            method.addInstruction(
                0,
                "invoke-static { p0 }, $EXTENSION_CLASS_DESCRIPTOR->refreshMenu(Landroid/app/Activity;)V",
            )
        }
    }
}
