package app.revanced.patches.reddit.customclients.baconreader.layout.thumbnails

import app.revanced.patcher.extensions.ExternalLabel
import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.extensions.removeInstruction
import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/baconreader/FixListThumbnailsPatch;"

@Suppress("unused")
val fixListThumbnailsPatch = bytecodePatch(
    name = "Fix list thumbnails",
    description = "Fixes empty thumbnails in the post list, shows a preview as thumbnail for link posts " +
        "and crossposted galleries, and blurs the thumbnails of NSFW and spoiler posts.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.onelouder.baconreader"("6.1.4"),
        "com.onelouder.baconreader.premium"("6.1.4"),
    )

    apply {
        // Utils.canShowThumbnail(Link) is checked before every list and widget thumbnail.
        // Fix the thumbnail URL there, and show blurred NSFW thumbnails the app would hide.
        canShowThumbnailMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static { p0 }, $EXTENSION_CLASS_DESCRIPTOR->fixThumbnail(Ljava/lang/Object;)V
                invoke-static { p0 }, $EXTENSION_CLASS_DESCRIPTOR->forceShow(Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :original
                return v0
            """,
            ExternalLabel("original", canShowThumbnailMethod.getInstruction(0)),
        )

        // LinkProcessor.loadThumbnail(Context, Link, ImageView) loads the thumbnail of a list row.
        loadThumbnailMethodMatch.method.apply {
            // The thumbnail is loaded once: a network hiccup leaves it empty. Try again when it fails,
            // and look up the thumbnails of RedGIFs posts, which Reddit does not always send.
            // Done before the instructions are added at the start, which move the matched index.
            val intoIndex = loadThumbnailMethodMatch[0]
            val into = getInstruction<FiveRegisterInstruction>(intoIndex)
            if (getInstruction(intoIndex + 1).opcode == Opcode.MOVE_RESULT_OBJECT) removeInstruction(intoIndex + 1)
            replaceInstruction(
                intoIndex,
                "invoke-static { v${into.registerC}, v${into.registerD}, p1 }, " +
                    "$EXTENSION_CLASS_DESCRIPTOR->loadThumbnail(Ljava/lang/Object;Landroid/widget/ImageView;Ljava/lang/Object;)V",
            )

            addInstructions(
                0,
                """
                    invoke-static { p1 }, $EXTENSION_CLASS_DESCRIPTOR->fixThumbnail(Ljava/lang/Object;)V
                    invoke-static { p1, p2 }, $EXTENSION_CLASS_DESCRIPTOR->beforeLoad(Ljava/lang/Object;Landroid/widget/ImageView;)V
                """,
            )
        }

        // The post list and the post header show the app's gallery indicator when Link.isGallery is true.
        // Crossposts of galleries are not marked as galleries: check the crossposted post too.
        listOf(linkHolderUpdateViewMethodMatch, detailLinkFragmentResetHeaderMethodMatch).forEach { match ->
            val method = match.method
            val index = match[0]
            val instruction = method.getInstruction<TwoRegisterInstruction>(index)
            method.replaceInstruction(
                index,
                "invoke-static { v${instruction.registerB} }, $EXTENSION_CLASS_DESCRIPTOR->isGallery(Ljava/lang/Object;)Ljava/lang/Boolean;",
            )
            method.addInstruction(index + 1, "move-result-object v${instruction.registerA}")
        }
    }
}
