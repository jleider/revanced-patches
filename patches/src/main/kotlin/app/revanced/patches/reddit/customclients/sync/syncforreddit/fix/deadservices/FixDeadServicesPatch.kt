package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.deadservices

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction.string() =
    ((this as? ReferenceInstruction)?.reference as? StringReference)?.string

@Suppress("unused")
val fixDeviantArtPatch = bytecodePatch(
    name = "Fix DeviantArt embeds",
    description = "Uses HTTPS for DeviantArt oEmbed lookups; the HTTP endpoint now redirects and fails.",
) {
    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    apply {
        deviantArtConstructorMatch.method.apply {
            val index = implementation!!.instructions.indexOfFirst {
                it.string() == "http://backend.deviantart.com/oembed?url="
            }
            if (index < 0) throw PatchException("DeviantArt oEmbed URL not found")
            val register = getInstruction<OneRegisterInstruction>(index).registerA
            replaceInstruction(index, "const-string v$register, \"https://backend.deviantart.com/oembed?url=\"")
        }
    }
}

@Suppress("unused")
val hideRestoreCommentPatch = bytecodePatch(
    name = "Hide restore comment",
    description = "Hides the \"Restore comment\" action. It relied on Pushshift, which no longer serves Reddit data, and failed silently.",
) {
    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    apply {
        // The item is added in a block guarded by `if-eqz vX, :skip` right before
        // `new-instance AbstractSelectionDialogBottomSheet$h; ...; const-string "Restore comment"`.
        // Force the guard register to 0 so the block is always skipped.
        commentActionsMethodMatch.method.apply {
            val instructions = implementation!!.instructions.toList()
            val labelIndex = instructions.indices.firstOrNull { i ->
                instructions[i].string() == "Restore comment" &&
                    (1..3).any { back -> i - back >= 0 && instructions[i - back].opcode == Opcode.NEW_INSTANCE }
            } ?: throw PatchException("Restore comment item not found")
            val newInstanceIndex = (labelIndex - 1 downTo 0).first { instructions[it].opcode == Opcode.NEW_INSTANCE }
            val guardIndex = newInstanceIndex - 1
            if (instructions[guardIndex].opcode != Opcode.IF_EQZ) {
                throw PatchException("Unexpected guard before Restore comment item: ${instructions[guardIndex].opcode}")
            }
            val register = getInstruction<OneRegisterInstruction>(guardIndex).registerA
            addInstruction(guardIndex, "const/4 v$register, 0x0")
        }
    }
}
