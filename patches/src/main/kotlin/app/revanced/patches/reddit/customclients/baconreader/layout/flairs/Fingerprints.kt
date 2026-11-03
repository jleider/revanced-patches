package app.revanced.patches.reddit.customclients.baconreader.layout.flairs

import app.revanced.patcher.definingClass
import app.revanced.patcher.gettingFirstMethodDeclaratively
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType

/** Opens the feed of MainActivity.linksetKey: on start, and when the subreddit, sort or query changes. */
internal val BytecodePatchContext.openLinksetMethod by gettingFirstMethodDeclaratively {
    definingClass("Lcom/onelouder/baconreader/ui/main/MainActivity;")
    name("openLinkset")
    returnType("V")
    parameterTypes()
}

/** Adds a page of posts from Reddit to a feed. */
internal val BytecodePatchContext.addToLinksetMethod by gettingFirstMethodDeclaratively {
    definingClass("Lcom/onelouder/baconreader/data/LinksetManager;")
    name("addToLinkset")
    returnType("Z")
    parameterTypes("Lcom/onelouder/baconreader/data/LinksetKey;", "Lcom/onelouder/baconreader/reddit/Listing;", "I", "J")
}
