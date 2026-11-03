package app.revanced.patches.reddit.customclients.baconreader.misc.extension.hooks

import app.revanced.patcher.definingClass
import app.revanced.patcher.gettingFirstMethodDeclaratively
import app.revanced.patcher.name
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType
import app.revanced.patches.shared.misc.extension.activityOnCreateExtensionHook

internal val initHook = activityOnCreateExtensionHook("Lcom/onelouder/baconreader/BaconReader;")

/** The app's Application.onCreate. */
internal val BytecodePatchContext.applicationOnCreateMethod by gettingFirstMethodDeclaratively {
    definingClass("Lcom/onelouder/baconreader/BaconReader;")
    name("onCreate")
    returnType("V")
}
