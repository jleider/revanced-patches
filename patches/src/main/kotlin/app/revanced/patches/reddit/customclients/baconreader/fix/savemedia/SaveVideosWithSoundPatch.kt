package app.revanced.patches.reddit.customclients.baconreader.fix.savemedia

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/baconreader/savemedia/VideoAudioPatch;"

@Suppress("unused")
val saveVideosWithSoundPatch = bytecodePatch(
    name = "Save videos with sound",
    description = "Adds the sound to Reddit videos saved with \"Save to device\", which were saved without it.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.onelouder.baconreader"("6.1.4"),
        "com.onelouder.baconreader.premium"("6.1.4"),
    )

    apply {
        // Reddit videos keep the sound in a separate file: the app saves only the picture (fallback_url).
        // After each download into the file (a first try, then a retry with the full url), add the audio track
        // when it succeeded. Not before the method's return: it is a branch target.
        downloadIntoFolderMethodMatch.method.apply {
            // The download calls and their move-result, from the last one so the indices do not move.
            listOf(2, 0).map { downloadIntoFolderMethodMatch[it] }.forEach { index ->
                val file = getInstruction<FiveRegisterInstruction>(index).registerE
                val result = getInstruction<OneRegisterInstruction>(index + 1).registerA
                addInstruction(
                    index + 2,
                    "invoke-static { p0, v$result, v$file }, $EXTENSION_CLASS_DESCRIPTOR->" +
                        "onDownloaded(Landroid/content/Context;ZLjava/lang/Object;)V",
                )
            }
        }
    }
}
