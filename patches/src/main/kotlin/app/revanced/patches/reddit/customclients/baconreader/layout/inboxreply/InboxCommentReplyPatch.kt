package app.revanced.patches.reddit.customclients.baconreader.layout.inboxreply

import app.revanced.patcher.extensions.ExternalLabel
import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/baconreader/inbox/InboxReplyPatch;"

@Suppress("unused")
val inboxCommentReplyPatch = bytecodePatch(
    name = "Inbox comment reply screen",
    description = "Opens the comment reply screen, with the replied comment or message, when replying from the " +
        "inbox, instead of the plain \"Send message\" form.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.onelouder.baconreader"("6.1.4"),
        "com.onelouder.baconreader.premium"("6.1.4"),
    )

    apply {
        // Comment replies, mentions and private messages open the comment reply screen.
        replyMessageMethod.addInstructionsWithLabels(
            0,
            """
                iget-object v0, p0, $INBOX_HELPER_DESCRIPTOR->activity:Landroid/app/Activity;
                invoke-static { v0, p1 }, $EXTENSION_CLASS_DESCRIPTOR->openReplyScreen(Landroid/app/Activity;Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :send_message
                return-void
            """,
            ExternalLabel("send_message", replyMessageMethod.getInstruction(0)),
        )

        // A private message has no subreddit: "Reply to [user] in r/null as [you]".
        updateReplyTextMethodMatch.method.apply {
            val setTextIndex = updateReplyTextMethodMatch[1]
            val viewRegister = getInstruction<FiveRegisterInstruction>(setTextIndex).registerC
            addInstruction(
                setTextIndex + 1,
                "invoke-static { v$viewRegister }, $EXTENSION_CLASS_DESCRIPTOR->fixReplyText(Landroid/widget/TextView;)V",
            )
        }
    }
}
