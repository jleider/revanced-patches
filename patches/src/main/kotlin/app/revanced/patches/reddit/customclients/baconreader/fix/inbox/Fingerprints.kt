package app.revanced.patches.reddit.customclients.baconreader.fix.inbox

import app.revanced.patcher.afterAtMost
import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.definingClass
import app.revanced.patcher.gettingFirstMethodDeclaratively
import app.revanced.patcher.instructions
import app.revanced.patcher.method
import app.revanced.patcher.name
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType

/** "Mark read" / "Mark unread" of an inbox item. */
internal val BytecodePatchContext.setPostStatusMethod by gettingFirstMethodDeclaratively {
    definingClass("Lcom/onelouder/baconreader/adapters/InboxHelper;")
    name("setPostStatus")
    returnType("V")
}

/** Shows the menu of an inbox item: Utils.getAlertBuilder(context), then builder.setAdapter(menu, listener). */
internal val BytecodePatchContext.inboxMenuMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/adapters/InboxHelper;")
    name("onOpen")
    returnType("V")
    instructions(
        method("getAlertBuilder"),
        afterAtMost(6, method("setAdapter")),
    )
}
