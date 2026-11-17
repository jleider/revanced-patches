package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.gfycat

import app.revanced.patcher.extensions.*
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.linkpreview.imageClientMethodMatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import app.revanced.util.getReference
import app.revanced.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val EXT = "Lapp/revanced/extension/syncforreddit/FixGfycatPatch;"

@Suppress("unused")
val fixGfycatPatch = bytecodePatch(
    name = "Fix Gfycat",
    description = "Plays Reddit's archived copies of Gfycat videos and fails immediately when none exists.",
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith("com.laurencedawson.reddit_sync", "com.laurencedawson.reddit_sync.pro", "com.laurencedawson.reddit_sync.dev")
    apply {
        parserMatch.method.apply {
            // .locals 29: v2 remains the post JSON; v5 is the final ContentValues.
            val index = implementation!!.instructions.indexOfLast {
                it.getReference<MethodReference>()?.let { r -> r.definingClass == "Ljava/util/ArrayList;" && r.name == "add" } == true
            }
            require(index >= 0)
            // Tripwires for the register analysis above: fail the patch, don't corrupt registers.
            check(implementation!!.registerCount - parameters.size == 29) { "PostParserHelper.a locals changed" }
            check(getInstruction<FiveRegisterInstruction>(index).registerD == 5) { "Post ContentValues no longer in v5" }
            addInstructions(index, "invoke-static { v2, v5 }, $EXT->repairPost(Lorg/json/JSONObject;Landroid/content/ContentValues;)V")
        }
        viewerMatch.method.apply {
            // The first internal (String)V call is the MLB direct streaming path.
            val play = implementation!!.instructions.first {
                it.opcode == Opcode.INVOKE_DIRECT && it.getReference<MethodReference>()?.let { r ->
                    r.definingClass == definingClass && r.parameterTypes == listOf("Ljava/lang/String;") && r.returnType == "V"
                } == true
            }.getReference<MethodReference>()!!
            // .locals 6, v0 is dead at entry. Direct v.redd.it MP4 links (Gfycat archives, shared
            // DASH links) play the post's DASH playlist (video + audio); Sync's own id extraction
            // breaks on ".mp4".
            check(implementation!!.registerCount - parameters.size >= 1) { "ImageViewerFragment.X3 locals changed" }
            addInstructionsWithLabels(0, """
                invoke-static { p1 }, $EXT->redditStream(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v0
                if-eqz v0, :original
                invoke-direct { p0, v0 }, $play
                return-void
            """, ExternalLabel("original", getInstruction(0)))
        }
        val serializer = serializerMatch.method
        val key = serializer.indexOfFirstInstructionOrThrow { getReference<StringReference>()?.string == "video_preview" }
        val getter = (0 until key).reversed().firstNotNullOf { serializer.getInstruction(it).getReference<MethodReference>() }
        autoplayMatch.method.apply {
            // Resolve Post URL/video getters and player from original instructions, not names.
            val calls = implementation!!.instructions.mapNotNull { it.getReference<MethodReference>() }
            val video = calls.first { it.name == getter.name && it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/String;" }
            val url = calls.first { it.definingClass == video.definingClass && it.name != video.name && it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/String;" }
            val play = calls.last { it.definingClass == definingClass && it.parameterTypes == listOf("Ljava/lang/String;", "Z", "Z") }
            // Three locals; p1 is still Post. Handle Gfycat before enhanced autoplay's proxy rewrite.
            addInstructionsWithLabels(0, """
                invoke-virtual { p1 }, $url
                move-result-object v0
                invoke-static { v0 }, $EXT->isGfycat(Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :original
                invoke-virtual { p1 }, $video
                move-result-object v0
                invoke-static { v0 }, $EXT->isRedditMp4(Ljava/lang/String;)Z
                move-result v1
                if-eqz v1, :done
                invoke-virtual { p0, v0, p2, p3 }, $play
                :done
                return-void
            """, ExternalLabel("original", getInstruction(0)))
        }
        queueMatch.method.apply {
            // Both locals dead at beginRequest, after bookkeeping. Independent of Imgur:
            // either ordering falls through the other patch's branch for unrelated hosts.
            val index = indexOfFirstInstructionOrThrow { getReference<MethodReference>()?.name == "beginRequest" }
            addInstructionsWithLabels(index, """
                invoke-virtual { p1 }, Lcom/android/volley/Request;->getUrl()Ljava/lang/String;
                move-result-object v0
                invoke-static { v0 }, $EXT->isGfycat(Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :network
                new-instance v0, Lcom/android/volley/VolleyError;
                const-string v1, "Gfycat has shut down and Reddit has no copy of this video"
                invoke-direct { v0, v1 }, Lcom/android/volley/VolleyError;-><init>(Ljava/lang/String;)V
                invoke-virtual { p0 }, Lcom/android/volley/RequestQueue;->getResponseDelivery()Lcom/android/volley/ResponseDelivery;
                move-result-object v1
                invoke-interface { v1, p1, v0 }, Lcom/android/volley/ResponseDelivery;->postError(Lcom/android/volley/Request;Lcom/android/volley/VolleyError;)V
                return-object p1
                # Fall through to whatever follows this block (the other RequestQueue hook or
                # beginRequest). Jumping to beginRequest directly would skip a hook inserted later.
                :network
                nop
            """)
        }
        // Glide: fail dead Gfycat posters/favicons locally (see GfycatBlockInterceptor).
        imageClientMethodMatch.method.apply {
            val addIndex = indexOfFirstInstructionOrThrow { getReference<MethodReference>()?.name == "addInterceptor" }
            check(getInstruction(addIndex + 1).opcode == Opcode.MOVE_RESULT_OBJECT)
            val builder = getInstruction<OneRegisterInstruction>(addIndex + 1).registerA
            addInstructions(addIndex + 2, """
                invoke-static { v$builder }, Lapp/revanced/extension/syncforreddit/GfycatBlockInterceptor;->addInterceptor(Lokhttp3/OkHttpClient${'$'}Builder;)Lokhttp3/OkHttpClient${'$'}Builder;
                move-result-object v$builder
            """)
        }
    }
}
