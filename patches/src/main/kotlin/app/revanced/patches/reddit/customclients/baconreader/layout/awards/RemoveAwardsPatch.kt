package app.revanced.patches.reddit.customclients.baconreader.layout.awards

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.extensions.replaceInstructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/baconreader/awards/RemoveAwardsPatch;"

@Suppress("unused")
val removeAwardsPatch = bytecodePatch(
    name = "Remove awards",
    description = "Removes the award icons, \"Give Award\" and \"View Award Details\". " +
        "Reddit's API no longer reports awards, and giving them used Reddit's discontinued coins.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.onelouder.baconreader",
        "com.onelouder.baconreader.premium",
    )

    apply {
        // Comment and post action bars: the award buttons are created outside the bar.
        controlBarSetButtonMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static { p1 }, $EXTENSION_CLASS_DESCRIPTOR->isAwardAction(Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :add_button
                new-instance v0, Landroid/widget/LinearLayout;
                iget-object v1, p0, $CONTROL_BAR_CLASS_DESCRIPTOR->activity:Landroid/app/Activity;
                invoke-direct { v0, v1 }, Landroid/widget/LinearLayout;-><init>(Landroid/content/Context;)V
                new-instance v1, $CONTROL_BAR_BUTTON_CLASS_DESCRIPTOR
                invoke-direct { v1, v0 }, $CONTROL_BAR_BUTTON_CLASS_DESCRIPTOR-><init>(Landroid/view/ViewGroup;)V
                invoke-virtual { v1, p1 }, $CONTROL_BAR_BUTTON_CLASS_DESCRIPTOR->setText(Ljava/lang/String;)$CONTROL_BAR_BUTTON_CLASS_DESCRIPTOR
                return-object v1
                :add_button
                nop
            """,
        )

        // The ⋮ menu of posts.
        controlbarBaseOnClickMethodMatch.method.apply {
            val showIndex = controlbarBaseOnClickMethodMatch[0]
            val popupRegister = getInstruction<FiveRegisterInstruction>(showIndex).registerC
            addInstruction(
                showIndex,
                "invoke-static { v$popupRegister }, $EXTENSION_CLASS_DESCRIPTOR->hideAwardMenuItems(Ljava/lang/Object;)V",
            )
        }

        // Award icons of posts and comments, in lists and on the post screen.
        listOf(hideAwardIconsMethod, hideAwardIconsOnDetailViewMethod).forEach { method ->
            method.replaceInstructions(
                0,
                """
                    const/4 v0, 0x1
                    return v0
                """,
            )
        }

        // The two "Hide Reddit Awards" settings no longer do anything: don't add them to the settings lists.
        settingsAdapterInitSettingsMethodMatch.let { match ->
            // list.add(setting): its result is not used.
            match.indices.forEach { indices -> match.method.replaceInstruction(indices[1], "nop") }
        }
    }
}
