package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.imgur

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.removeInstructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.revanced.util.getReference
import app.revanced.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/syncforreddit/FixImgurPatch;"

@Suppress("unused")
val fixImgurPatch = bytecodePatch(
    name = "Fix Imgur",
    description = "Resolves Imgur images locally and loads albums from Imgur's public embed page, without an API key.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    apply {
        // These static builders still receive the original URL, including its animation suffix.
        // Replace their bodies so neither the proxy nor its salted MD5 builder is used.
        listOf(singleUrlMethodMatch.method to "singleUrl", albumUrlMethodMatch.method to "albumUrl")
            .forEach { (method, helper) ->
                method.removeInstructions(0, method.implementation!!.instructions.size)
                method.addInstructions(
                    0,
                    """
                        invoke-static { p0 }, $EXTENSION_CLASS_DESCRIPTOR->$helper(Ljava/lang/String;)Ljava/lang/String;
                        move-result-object p0
                        return-object p0
                    """,
                )
            }
        getAlbumHeadersMethodMatch.method.apply {
            removeInstructions(0, implementation!!.instructions.size)
            addInstructions(
                0,
                """
                    invoke-static { }, $EXTENSION_CLASS_DESCRIPTOR->albumHeaders()Ljava/util/Map;
                    move-result-object v0
                    return-object v0
                """,
            )
        }

        // Albums: the request now fetches imgur.com/a/<id>/embed; turn that page into the API-shaped
        // JSON the parser below reads. .locals 11, v0-v2 are free at entry; keep the response headers
        // for Volley's cache. A null result leaves the response alone (parser error -> browser).
        albumParseMethodMatch.method.apply {
            check(implementation!!.registerCount - parameters.size - 1 >= 3) { "ImgurGalleryRequest.parseNetworkResponse locals changed" }
            addInstructionsWithLabels(
                0,
                """
                    iget-object v0, p1, Lcom/android/volley/NetworkResponse;->data:[B
                    invoke-static { v0 }, $EXTENSION_CLASS_DESCRIPTOR->albumJson([B)[B
                    move-result-object v0
                    if-eqz v0, :parse
                    iget-object v1, p1, Lcom/android/volley/NetworkResponse;->headers:Ljava/util/Map;
                    new-instance v2, Lcom/android/volley/NetworkResponse;
                    invoke-direct { v2, v0, v1 }, Lcom/android/volley/NetworkResponse;-><init>([BLjava/util/Map;)V
                    move-object p1, v2
                    :parse
                    nop
                """,
            )
        }

        // RequestQueue.add has two locals. At beginRequest both are dead, and request
        // bookkeeping is complete. Use normal Volley delivery for cancellation and cleanup.
        queueAddMethodMatch.method.apply {
            val index = indexOfFirstInstructionOrThrow {
                getReference<MethodReference>()?.name == "beginRequest"
            }
            addInstructionsWithLabels(
                index,
                """
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
                """,
            )
        }
    }
}
