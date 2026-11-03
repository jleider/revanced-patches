package app.revanced.patches.reddit.customclients.baconreader.layout.textposticon

import app.revanced.patcher.extensions.ExternalLabel
import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.all.misc.resources.addResources
import app.revanced.patches.all.misc.resources.addResourcesPatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/baconreader/TextPostIconPatch;"

@Suppress("unused")
val textPostIconPatch = bytecodePatch(
    name = "Text post icon",
    description = "Replaces the expand button of text posts in the post list, which looks like a video " +
        "play button, with a page icon and a chevron that flips when the text is expanded.",
) {
    dependsOn(sharedExtensionPatch, addResourcesPatch)

    compatibleWith(
        "com.onelouder.baconreader"("6.1.4"),
        "com.onelouder.baconreader.premium"("6.1.4"),
    )

    apply {
        addResources("baconreader", "layout.textposticon.textPostIconPatch")

        mapOf(
            LINK_HOLDER_CLASS_DESCRIPTOR to (linkHolderConstructorMethodMatch to linkHolderRotateSelftextArrowMethod),
            LINK_CARD_HOLDER_CLASS_DESCRIPTOR to (linkCardHolderConstructorMethodMatch to linkCardHolderRotateSelftextArrowMethod),
        ).forEach { (holder, methods) ->
            val (constructorMatch, rotateSelftextArrow) = methods

            // Replace the icon right after "this.next = (ImageView) findViewById(R.id.next)".
            val constructor = constructorMatch.method
            val setNextIndex = constructorMatch[0]
            val buttonRegister = constructor.getInstruction<TwoRegisterInstruction>(setNextIndex).registerA
            constructor.addInstruction(
                setNextIndex + 1,
                "invoke-static { v$buttonRegister }, $EXTENSION_CLASS_DESCRIPTOR->install(Landroid/widget/ImageView;)V",
            )

            // rotateSelftextArrow(expanded, duration): flip the chevron instead of rotating the whole icon.
            rotateSelftextArrow.addInstructionsWithLabels(
                0,
                """
                    iget-object v0, p0, $holder->next:Landroid/widget/ImageView;
                    invoke-static { v0, p1, p2 }, $EXTENSION_CLASS_DESCRIPTOR->setExpanded(Landroid/widget/ImageView;ZI)Z
                    move-result v0
                    if-eqz v0, :original
                    iput-boolean p1, p0, $holder->rotated:Z
                    return-void
                """,
                ExternalLabel("original", rotateSelftextArrow.getInstruction(0)),
            )
        }
    }
}
