package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.youtube

import app.revanced.patcher.*
import app.revanced.patcher.extensions.*
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch

private const val RESULT = "Lcom/google/android/youtube/player/YouTubeInitializationResult;"

// CustomYouTubeVideoActivity's YouTubePlayer.OnInitializedListener failure callback.
internal val BytecodePatchContext.initFailureMethodMatch by composingFirstMethod {
    returnType("V")
    parameterTypes("Lcom/google/android/youtube/player/YouTubePlayer\$Provider;", RESULT)
    instructions(string { this == "SERVICE_DISABLED" }, string { this == "SERVICE_INVALID" })
    custom { immutableClassDef.sourceFile == "CustomYouTubeVideoActivity.java" }
}

// CustomYouTubeVideoActivity's player setup: the only method that starts Google's player
// (YouTubePlayerView.initialize with Sync's API key). Private, no args, .locals 2.
internal val BytecodePatchContext.playerSetupMethodMatch by composingFirstMethod {
    returnType("V")
    parameterTypes()
    instructions(string { startsWith("AIza") })
    custom { immutableClassDef.sourceFile == "CustomYouTubeVideoActivity.java" && name != "onActivityResult" }
}

@Suppress("unused")
val fixYouTubePatch = bytecodePatch(
    name = "Fix YouTube player",
    description = "Plays YouTube videos inside Sync with YouTube's embed player, because current YouTube apps removed the player service Sync used. Videos that can't be embedded open in the YouTube app.",
    use = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith("com.laurencedawson.reddit_sync", "com.laurencedawson.reddit_sync.pro", "com.laurencedawson.reddit_sync.dev")
    apply {
        playerSetupMethodMatch.method.apply {
            // v0 is free at entry. If YouTube's player service is missing, the web player takes over the
            // activity and Google's player is never initialized.
            check(implementation!!.registerCount - parameters.size - 1 >= 1) { "player setup has no free register" }
            addInstructionsWithLabels(0, """
                invoke-static { p0 }, Lapp/revanced/extension/syncforreddit/YouTubeWebPlayer;->show(Landroid/app/Activity;)Z
                move-result v0
                if-eqz v0, :google
                return-void
                :google
                nop
            """)
        }
        // Any other failure of Google's player (service present but unusable): open in the YouTube app.
        initFailureMethodMatch.method.apply {
            // .locals 4; v0 is free at entry. p0 = activity, p2 = YouTubeInitializationResult.
            addInstructionsWithLabels(0, """
                invoke-static { p0, p2 }, Lapp/revanced/extension/syncforreddit/FixYouTubePatch;->openExternally(Landroid/app/Activity;Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :sync
                return-void
                :sync
                nop
            """)
        }
    }
}
