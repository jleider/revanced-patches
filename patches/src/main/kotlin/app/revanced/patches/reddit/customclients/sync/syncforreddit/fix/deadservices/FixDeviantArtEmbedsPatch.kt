package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.deadservices

import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
val fixDeviantArtEmbedsPatch = bytecodePatch(
    name = "Fix DeviantArt embeds",
    description = "Uses HTTPS for DeviantArt oEmbed lookups; the HTTP endpoint now redirects and fails.",
) {
    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    apply {
        deviantArtConstructorMatch.method.apply {
            val index = implementation!!.instructions.indexOfFirst {
                it.stringValue() == "http://backend.deviantart.com/oembed?url="
            }
            if (index < 0) throw PatchException("DeviantArt oEmbed URL not found")
            val register = getInstruction<OneRegisterInstruction>(index).registerA
            replaceInstruction(index, "const-string v$register, \"https://backend.deviantart.com/oembed?url=\"")
        }
    }
}
