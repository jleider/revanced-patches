package app.revanced.patches.reddit.customclients.baconreader.layout.random

import app.revanced.patcher.afterAtMost
import app.revanced.patcher.allOf
import app.revanced.patcher.anyOf
import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.definingClass
import app.revanced.patcher.instructions
import app.revanced.patcher.invoke
import app.revanced.patcher.method
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType
import com.android.tools.smali.dexlib2.Opcode

/**
 * Fills the feed list of the post list (Front Page, All, Popular, Random, My Random, Friends, subreddits):
 * list.add(new FeedItem("Random", ...)) and the same for "My Random" (RedditId.RANDOM and RedditId.MYRANDOM).
 */
internal val BytecodePatchContext.feedAdapterResetMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/adapters/FeedAdapter;")
    name("reset")
    returnType("V")
    parameterTypes()
    instructions(*addFeed("Random"))
    instructions(*addFeed("My Random"))
}

context(_: MutableList<String>)
private fun addFeed(feed: String) = arrayOf(
    feed(),
    afterAtMost(2, allOf(anyOf(Opcode.INVOKE_VIRTUAL(), Opcode.INVOKE_INTERFACE()), method("add"))),
)
