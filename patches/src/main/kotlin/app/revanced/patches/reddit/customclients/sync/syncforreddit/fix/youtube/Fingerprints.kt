package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.youtube

import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.custom
import app.revanced.patcher.definingClass
import app.revanced.patcher.gettingFirstMethodDeclaratively
import app.revanced.patcher.immutableClassDef
import app.revanced.patcher.instructions
import app.revanced.patcher.invoke
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType
import app.revanced.patcher.string

private const val INITIALIZATION_RESULT_CLASS_DESCRIPTOR = "Lcom/google/android/youtube/player/YouTubeInitializationResult;"

// CustomYouTubeVideoActivity's YouTubePlayer.OnInitializedListener failure callback.
internal val BytecodePatchContext.initFailureMethodMatch by composingFirstMethod {
    returnType("V")
    parameterTypes("Lcom/google/android/youtube/player/YouTubePlayer\$Provider;", INITIALIZATION_RESULT_CLASS_DESCRIPTOR)
    instructions("SERVICE_DISABLED"(), "SERVICE_INVALID"())
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

internal val BytecodePatchContext.blockAdsMethod by gettingFirstMethodDeclaratively {
    definingClass("Lapp/revanced/extension/syncforreddit/YouTubeWebPlayer;")
    name("blockAds")
}
