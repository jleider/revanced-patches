package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.redditvideo

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.invoke
import app.revanced.patcher.name
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch

@Suppress("unused")
val fixRedditVideoPlaybackPatch = bytecodePatch(
    name = "Fix Reddit video playback",
    description = "Plays Reddit videos through their DASH stream, because Reddit now refuses the HLS stream of many new videos.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    apply {
        // Inline/feed autoplay. Static method: p0 is the URL; rewrite it before Sync logs and classifies it.
        mediaSourceMethodMatch.method.addInstructions(
            0,
            """
                invoke-static { p0 }, Lapp/revanced/extension/syncforreddit/FixRedditVideoPatch;->videoSource(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p0
            """,
        )
        // Instance method: p1 is the URL; rewrite it before it is stored in mSource. No locals needed.
        viewerSourceMethodMatch.method.addInstructions(
            0,
            """
                invoke-static { p1 }, Lapp/revanced/extension/syncforreddit/FixRedditVideoPatch;->videoSource(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p1
            """,
        )
    }
}
