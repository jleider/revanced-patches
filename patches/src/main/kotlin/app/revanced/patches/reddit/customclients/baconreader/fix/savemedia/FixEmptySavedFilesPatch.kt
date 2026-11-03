package app.revanced.patches.reddit.customclients.baconreader.fix.savemedia

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/baconreader/savemedia/FixEmptySavedFilesPatch;"

@Suppress("unused")
val fixEmptySavedFilesPatch = bytecodePatch(
    name = "Fix empty saved files",
    description = "Fixes saving media to the device also creating an empty file.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.onelouder.baconreader",
        "com.onelouder.baconreader.premium",
    )

    apply {
        // The app creates "<title>.jpg" first, then downloads to "<title>.<extension of the URL>".
        // Before each download, remove the first file if it is another, empty file.
        downloadToFileMethod.addInstruction(
            0,
            "invoke-static { p0, p2 }, $EXTENSION_CLASS_DESCRIPTOR->onDownloadTarget(Ljava/lang/Object;Ljava/lang/Object;)V",
        )
    }
}
