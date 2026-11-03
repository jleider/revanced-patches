package app.revanced.patches.reddit.customclients.baconreader.fix.inbox

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/baconreader/inbox/UnreadMessagesPatch;"

@Suppress("unused")
val fixUnreadMessagesPatch = bytecodePatch(
    name = "Fix unread messages",
    description = "Turns the orange inbox envelope back to normal when the last unread message is marked as read, " +
        "and removes \"Mark unread\", which Reddit no longer supports.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.onelouder.baconreader"("6.1.4"),
        "com.onelouder.baconreader.premium"("6.1.4"),
    )

    apply {
        // "Mark read" only told Reddit and never updated the envelope.
        setPostStatusMethod.addInstructions(
            0,
            """
                invoke-static { p1 }, $EXTENSION_CLASS_DESCRIPTOR->setPostStatus(Ljava/lang/Object;)V
                return-void
            """,
        )

        // Reddit answers api/unread_message with 404: "Mark unread" did nothing.
        inboxMenuMethodMatch.method.apply {
            val builderIndex = inboxMenuMethodMatch[0]
            // builder.setAdapter(menu, listener): the menu, already created before the builder.
            val adapterRegister = getInstruction<FiveRegisterInstruction>(inboxMenuMethodMatch[1]).registerD
            addInstruction(
                builderIndex,
                "invoke-static { v$adapterRegister }, $EXTENSION_CLASS_DESCRIPTOR->" +
                    "removeMarkUnread(Landroid/widget/ArrayAdapter;)V",
            )
        }
    }
}
