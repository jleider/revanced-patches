package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.imgur

import app.revanced.patcher.extensions.*
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.stringOption
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.revanced.util.getReference
import app.revanced.util.indexOfFirstInstructionOrThrow
import app.revanced.util.returnEarly
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/syncforreddit/FixImgurPatch;"

@Suppress("unused")
val fixImgurPatch = bytecodePatch(
    name = "Fix Imgur",
    description = "Resolves Imgur images locally and optionally uses a personal Imgur client ID for albums.",
    use = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    val clientId by stringOption(
        name = "Imgur client ID",
        default = null,
        description = "Optional Imgur API client ID for albums. Without it, albums fail cleanly.",
        required = false,
    )

    apply {
        val id = clientId?.trim().orEmpty()
        require(id.all { it.isLetterOrDigit() && it.code < 128 }) { "Invalid Imgur client ID" }
        imgurClientIdMethod.returnEarly(id)

        // These static builders still receive the original URL, including its animation suffix.
        // Replace their bodies so neither the proxy nor its salted MD5 builder is used.
        listOf(singleUrlMethodMatch.method to "singleUrl", albumUrlMethodMatch.method to "albumUrl")
            .forEach { (method, helper) ->
                method.removeInstructions(0, method.implementation!!.instructions.size)
                method.addInstructions(0, """
                    invoke-static { p0 }, $EXTENSION_CLASS_DESCRIPTOR->$helper(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object p0
                    return-object p0
                """)
            }
        albumHeadersMethodMatch.method.apply {
            removeInstructions(0, implementation!!.instructions.size)
            addInstructions(0, """
                invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->albumHeaders()Ljava/util/Map;
                move-result-object v0
                return-object v0
            """)
        }

        // RequestQueue.add has two locals. At beginRequest both are dead, and request
        // bookkeeping is complete. Use normal Volley delivery for cancellation and cleanup.
        queueAddMethodMatch.method.apply {
            val index = indexOfFirstInstructionOrThrow {
                getReference<MethodReference>()?.name == "beginRequest"
            }
            addInstructionsWithLabels(index, """
                invoke-virtual { p1 }, Lcom/android/volley/Request;->getUrl()Ljava/lang/String;
                move-result-object v0
                invoke-static { v0 }, $EXTENSION_CLASS_DESCRIPTOR->localResponse(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v0
                if-eqz v0, :network
                invoke-virtual { v0 }, Ljava/lang/String;->getBytes()[B
                move-result-object v0
                new-instance v1, Lcom/android/volley/NetworkResponse;
                invoke-direct { v1, v0 }, Lcom/android/volley/NetworkResponse;-><init>([B)V
                invoke-virtual { p1, v1 }, Lcom/android/volley/Request;->parseNetworkResponse(Lcom/android/volley/NetworkResponse;)Lcom/android/volley/Response;
                move-result-object v0
                invoke-virtual { p0 }, Lcom/android/volley/RequestQueue;->getResponseDelivery()Lcom/android/volley/ResponseDelivery;
                move-result-object v1
                invoke-interface { v1, p1, v0 }, Lcom/android/volley/ResponseDelivery;->postResponse(Lcom/android/volley/Request;Lcom/android/volley/Response;)V
                return-object p1
                # Fall through to whatever follows this block (the other RequestQueue hook or
                # beginRequest). Jumping to beginRequest directly would skip a hook inserted later.
                :network
                nop
            """)
        }
    }
}
