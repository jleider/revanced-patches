package app.revanced.patches.reddit.customclients.baconreader.layout.inboxreply

import app.revanced.patcher.afterAtMost
import app.revanced.patcher.allOf
import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.definingClass
import app.revanced.patcher.field
import app.revanced.patcher.gettingFirstMethodDeclaratively
import app.revanced.patcher.instructions
import app.revanced.patcher.invoke
import app.revanced.patcher.method
import app.revanced.patcher.name
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType
import com.android.tools.smali.dexlib2.Opcode

internal const val INBOX_HELPER_DESCRIPTOR = "Lcom/onelouder/baconreader/adapters/InboxHelper;"

/** "Reply" of an inbox item: opens the "Send message" form. */
internal val BytecodePatchContext.replyMessageMethod by gettingFirstMethodDeclaratively {
    definingClass(INBOX_HELPER_DESCRIPTOR)
    name("replyMessage")
    returnType("V")
}

/** Shows "Reply to [user] in r/[subreddit] as [you]" and the replied text on the reply screen. */
internal val BytecodePatchContext.updateReplyTextMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/PostActivity;")
    name("updateReplyText")
    returnType("V")
    // this.subTextView.setText("Reply to [user] in r/... as [you]").
    instructions(
        allOf(Opcode.IGET_OBJECT(), field("subTextView")),
        afterAtMost(2, allOf(Opcode.INVOKE_VIRTUAL(), method("setText"))),
    )
}
