package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.redditvideo

import app.revanced.patcher.*
import app.revanced.patcher.extensions.*
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.AccessFlags

// ExoplayerUtils.a(String): the one place Sync turns a URL into an ExoPlayer MediaSource
// (DASH for v.redd.it ".mpd", HLS for v.redd.it ".m3u8", progressive otherwise).
internal val BytecodePatchContext.mediaSourceMethodMatch by composingFirstMethod {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
    parameterTypes("Ljava/lang/String;")
    returnType("Lcom/google/android/exoplayer2/source/MediaSource;")
    instructions(string { this == "EXOPLAYER SOURCE: " })
    custom { immutableClassDef.sourceFile == "ExoplayerUtils.java" }
}

// CustomExoPlayerView.setSource(url, hd, muted): the full-screen viewer builds its own MediaSource
// from this URL (HLS / DASH / progressive by the same URL checks), not through ExoplayerUtils.
internal val BytecodePatchContext.viewerSourceMethodMatch by composingFirstMethod {
    name("setSource")
    parameterTypes("Ljava/lang/String;", "Z", "Z")
    returnType("V")
    custom { immutableClassDef.sourceFile == "CustomExoPlayerView.java" }
}

@Suppress("unused")
val fixRedditVideoPlaybackPatch = bytecodePatch(
    name = "Fix Reddit video playback",
    description = "Plays Reddit videos through their DASH stream, because Reddit now refuses the HLS stream of many new videos.",
    use = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith("com.laurencedawson.reddit_sync", "com.laurencedawson.reddit_sync.pro", "com.laurencedawson.reddit_sync.dev")
    apply {
        // Inline/feed autoplay. Static method: p0 is the URL; rewrite it before Sync logs and classifies it.
        mediaSourceMethodMatch.method.addInstructions(0, """
            invoke-static { p0 }, Lapp/revanced/extension/syncforreddit/FixRedditVideoPatch;->videoSource(Ljava/lang/String;)Ljava/lang/String;
            move-result-object p0
        """)
        // Instance method: p1 is the URL; rewrite it before it is stored in mSource. No locals needed.
        viewerSourceMethodMatch.method.addInstructions(0, """
            invoke-static { p1 }, Lapp/revanced/extension/syncforreddit/FixRedditVideoPatch;->videoSource(Ljava/lang/String;)Ljava/lang/String;
            move-result-object p1
        """)
    }
}
