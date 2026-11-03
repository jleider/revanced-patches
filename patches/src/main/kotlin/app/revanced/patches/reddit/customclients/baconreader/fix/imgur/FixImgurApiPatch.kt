package app.revanced.patches.reddit.customclients.baconreader.fix.imgur

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.extensions.replaceInstructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/baconreader/imgur/FixImgurApiPatch;"

@Suppress("unused")
val fixImgurApiPatch = bytecodePatch(
    name = "Fix Imgur API",
    description = "Fixes Imgur albums and galleries not loading, and image uploads in comments and posts failing.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.onelouder.baconreader",
        "com.onelouder.baconreader.premium",
    )

    apply {
        // The app calls Imgur's API through a RapidAPI proxy whose subscription ended (403 for every request).
        // Imgur's own API accepts the same requests and client ID.
        getImgurBaseUrlMethod.replaceInstructions(
            0,
            """
                const-string v0, "https://api.imgur.com/3/"
                return-object v0
            """,
        )

        // Imgur's own API rejects the upload without a content type.
        imgurUploadMethodMatch.method.apply {
            val authorizationIndex = imgurUploadMethodMatch[0]
            val connectionRegister = getInstruction<FiveRegisterInstruction>(authorizationIndex).registerD
            addInstruction(
                authorizationIndex + 1,
                "invoke-static { v$connectionRegister }, " +
                    "$EXTENSION_CLASS_DESCRIPTOR->setUploadContentType(Ljava/net/HttpURLConnection;)V",
            )
        }
    }
}
