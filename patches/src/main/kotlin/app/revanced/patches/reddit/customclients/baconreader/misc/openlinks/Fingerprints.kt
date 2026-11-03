package app.revanced.patches.reddit.customclients.baconreader.misc.openlinks

import app.revanced.patcher.definingClass
import app.revanced.patcher.gettingFirstMethodDeclaratively
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType

internal val BytecodePatchContext.mainActivityOnResumeMethod by gettingFirstMethodDeclaratively {
    definingClass("Lcom/onelouder/baconreader/ui/main/MainActivity;")
    name("onResume")
    returnType("V")
    parameterTypes()
}
