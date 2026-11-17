package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.deadservices

import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.extensions.replaceInstruction
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
        deviantArtConstructorMethodMatch.method.apply {
            val index = deviantArtConstructorMethodMatch[0]
            val register = getInstruction<OneRegisterInstruction>(index).registerA
            replaceInstruction(index, "const-string v$register, \"https://backend.deviantart.com/oembed?url=\"")
        }
    }
}
