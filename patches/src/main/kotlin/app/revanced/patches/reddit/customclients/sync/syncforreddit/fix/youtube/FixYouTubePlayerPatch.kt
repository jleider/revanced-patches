package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.youtube

import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.invoke
import app.revanced.patcher.name
import app.revanced.patcher.patch.booleanOption
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.revanced.util.returnEarly

@Suppress("unused")
val fixYouTubePlayerPatch = bytecodePatch(
    name = "Fix YouTube player",
    description = "Plays YouTube videos inside Sync with YouTube's embed player, because current YouTube apps removed the player service Sync used. Videos that can't be embedded open in the YouTube app.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    val blockAds by booleanOption(
        name = "Block YouTube ads",
        default = true,
        description = "Removes ads from videos played in Sync's YouTube player. Ads YouTube stitches into the video stream itself can still appear.",
    )

    apply {
        blockAdsMethod.returnEarly(blockAds != false)
        playerSetupMethodMatch.method.apply {
            // v0 is free at entry. If YouTube's player service is missing, the web player takes over the
            // activity and Google's player is never initialized.
            check(implementation!!.registerCount - parameters.size - 1 >= 1) { "player setup has no free register" }
            addInstructionsWithLabels(
                0,
                """
                    invoke-static { p0 }, Lapp/revanced/extension/syncforreddit/YouTubeWebPlayer;->show(Landroid/app/Activity;)Z
                    move-result v0
                    if-eqz v0, :google
                    return-void
                    :google
                    nop
                """,
            )
        }
        // Any other failure of Google's player (service present but unusable): open in the YouTube app.
        initFailureMethodMatch.method.apply {
            // .locals 4; v0 is free at entry. p0 = activity, p2 = YouTubeInitializationResult.
            addInstructionsWithLabels(
                0,
                """
                    invoke-static { p0, p2 }, Lapp/revanced/extension/syncforreddit/FixYouTubePatch;->openExternally(Landroid/app/Activity;Ljava/lang/Object;)Z
                    move-result v0
                    if-eqz v0, :sync
                    return-void
                    :sync
                    nop
                """,
            )
        }
    }
}
