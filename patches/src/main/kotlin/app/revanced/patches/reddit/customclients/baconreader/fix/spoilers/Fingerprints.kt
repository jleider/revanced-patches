package app.revanced.patches.reddit.customclients.baconreader.fix.spoilers

import app.revanced.patcher.definingClass
import app.revanced.patcher.gettingFirstMethodDeclaratively
import app.revanced.patcher.name
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType

/** If an element of a comment's or post's HTML is a spoiler (>!...!<) the app hides. */
internal val BytecodePatchContext.isInternalSpoilerMethod by gettingFirstMethodDeclaratively {
    definingClass("Lcom/onelouder/baconreader/parser/RedditSpanner;")
    name("isInternalSpoiler")
    returnType("Z")
}
