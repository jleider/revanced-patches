package app.revanced.patches.reddit.customclients.baconreader.layout.sidebar

import app.revanced.patcher.definingClass
import app.revanced.patcher.gettingFirstMethodDeclaratively
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType

internal const val MAIN_ACTIVITY_CLASS_DESCRIPTOR = "Lcom/onelouder/baconreader/ui/main/MainActivity;"

internal val BytecodePatchContext.mainActivityOnPrepareOptionsMenuMethod by gettingFirstMethodDeclaratively {
    definingClass(MAIN_ACTIVITY_CLASS_DESCRIPTOR)
    name("onPrepareOptionsMenu")
    returnType("Z")
    parameterTypes("Landroid/view/Menu;")
}

internal val BytecodePatchContext.mainActivityOpenLinksetMethod by gettingFirstMethodDeclaratively {
    definingClass(MAIN_ACTIVITY_CLASS_DESCRIPTOR)
    name("openLinkset")
    returnType("V")
    parameterTypes()
}

/** Called when the posts of the feed are loaded. */
internal val BytecodePatchContext.mainActivityOnFirstLinkMethod by gettingFirstMethodDeclaratively {
    definingClass(MAIN_ACTIVITY_CLASS_DESCRIPTOR)
    name("onFirstLink")
    returnType("V")
    parameterTypes("Lcom/onelouder/baconreader/reddit/Link;")
}
