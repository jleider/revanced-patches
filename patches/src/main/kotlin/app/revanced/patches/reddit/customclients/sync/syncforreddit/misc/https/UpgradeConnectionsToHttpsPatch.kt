package app.revanced.patches.reddit.customclients.sync.syncforreddit.misc.https

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.name
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch

@Suppress("unused")
val upgradeConnectionsToHttpsPatch = bytecodePatch(
    name = "Upgrade connections to HTTPS",
    description = "Loads http:// links, images and videos over HTTPS, falling back to HTTP only for hosts without working HTTPS.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    apply {
        buildOkHttpClientMethodMatch.method.addInstructions(
            0,
            """
                invoke-static { p0 }, Lapp/revanced/extension/syncforreddit/HttpsUpgradeInterceptor;->install(Lokhttp3/OkHttpClient${'$'}Builder;)Lokhttp3/OkHttpClient${'$'}Builder;
            """,
        )
    }
}
