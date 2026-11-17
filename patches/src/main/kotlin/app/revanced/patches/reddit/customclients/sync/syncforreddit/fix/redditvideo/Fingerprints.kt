package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.redditvideo

import app.revanced.patcher.accessFlags
import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.custom
import app.revanced.patcher.immutableClassDef
import app.revanced.patcher.instructions
import app.revanced.patcher.invoke
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType
import com.android.tools.smali.dexlib2.AccessFlags

// The shared ExoPlayer utility turns a URL into an ExoPlayer MediaSource
// (DASH for v.redd.it ".mpd", HLS for v.redd.it ".m3u8", progressive otherwise).
internal val BytecodePatchContext.mediaSourceMethodMatch by composingFirstMethod {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
    parameterTypes("Ljava/lang/String;")
    returnType("Lcom/google/android/exoplayer2/source/MediaSource;")
    instructions("EXOPLAYER SOURCE: "())
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
