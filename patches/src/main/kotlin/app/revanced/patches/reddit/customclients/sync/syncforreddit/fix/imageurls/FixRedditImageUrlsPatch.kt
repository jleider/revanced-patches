package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.imageurls

import app.revanced.patcher.extensions.*
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.linkpreview.imageClientMethodMatch
import app.revanced.util.getReference
import app.revanced.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val fixRedditImageUrlsPatch = bytecodePatch(
    name = "Fix Reddit image URLs",
    description = "Unescapes '&amp;' in preview.redd.it image URLs, which Reddit's image CDN now rejects with 403.",
    use = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith("com.laurencedawson.reddit_sync", "com.laurencedawson.reddit_sync.pro", "com.laurencedawson.reddit_sync.dev")
    apply {
        // Glide's OkHttp client (OkHttpHelper). Same insertion point as GfycatBlockInterceptor.
        imageClientMethodMatch.method.apply {
            val addIndex = indexOfFirstInstructionOrThrow { getReference<MethodReference>()?.name == "addInterceptor" }
            check(getInstruction(addIndex + 1).opcode == Opcode.MOVE_RESULT_OBJECT)
            val builder = getInstruction<OneRegisterInstruction>(addIndex + 1).registerA
            addInstructions(addIndex + 2, """
                invoke-static { v$builder }, Lapp/revanced/extension/syncforreddit/RedditImageUrlInterceptor;->addInterceptor(Lokhttp3/OkHttpClient${'$'}Builder;)Lokhttp3/OkHttpClient${'$'}Builder;
                move-result-object v$builder
            """)
        }
    }
}
