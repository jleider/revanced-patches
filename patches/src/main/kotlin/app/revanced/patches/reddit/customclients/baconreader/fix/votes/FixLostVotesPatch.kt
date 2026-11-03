package app.revanced.patches.reddit.customclients.baconreader.fix.votes

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/baconreader/votes/FixLostVotesPatch;"

@Suppress("unused")
val fixLostVotesPatch = bytecodePatch(
    name = "Fix lost post votes",
    description = "Fixes votes on a post disappearing from the post list when made while its comments load.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.onelouder.baconreader"("6.1.4"),
        "com.onelouder.baconreader.premium"("6.1.4"),
    )

    apply {
        // Remember each vote sent to Reddit.
        voteMethod.addInstruction(
            0,
            "invoke-static { p1, p2 }, $EXTENSION_CLASS_DESCRIPTOR->onVote(ILjava/lang/String;)V",
        )

        // The post that comes with the comments was requested before the vote: apply it before the post is saved
        // to the post list.
        commentsLoadedMethodMatch.method.apply {
            val updateIndex = commentsLoadedMethodMatch[0]
            val linkRegister = getInstruction<FiveRegisterInstruction>(updateIndex).registerC
            addInstruction(
                updateIndex,
                "invoke-static { v$linkRegister }, $EXTENSION_CLASS_DESCRIPTOR->keepVote(Ljava/lang/Object;)V",
            )
        }
    }
}
