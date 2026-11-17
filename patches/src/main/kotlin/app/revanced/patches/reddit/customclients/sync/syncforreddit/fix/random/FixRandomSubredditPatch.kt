package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.random

import app.revanced.patcher.extensions.ExternalLabel
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.multisub.redditRequestConstructorMethodMatch
import app.revanced.util.getReference
import app.revanced.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/syncforreddit/FixRandomSubredditPatch;"

@Suppress("unused")
val fixRandomSubredditPatch = bytecodePatch(
    name = "Fix random subreddit",
    description = "Picks a random subreddit from Reddit's popular list, because r/random was removed. " +
        "Links to r/random and r/randnsfw open the random subreddit picker.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    apply {
        // RandomRequest extends RedditRequest; rewrite r/random(nsfw).json in the base constructor.
        redditRequestConstructorMethodMatch.method.addInstructions(
            0,
            """
                invoke-static { p3 }, $EXTENSION_CLASS_DESCRIPTOR->rewriteUrl(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p3
            """,
        )

        // .locals 3: right after `v1 = new String(bytes)`, v2 (the byte array) is dead and reassigned next.
        randomParseMethodMatch.method.apply {
            val index = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_DIRECT &&
                    getReference<MethodReference>()!!.let { reference ->
                        reference.definingClass == "Ljava/lang/String;" && reference.name == "<init>" &&
                            reference.parameterTypes.map(CharSequence::toString) == listOf("[B")
                    }
            }
            val bodyRegister = getInstruction<FiveRegisterInstruction>(index).registerC
            val scratchRegister = if (bodyRegister == 2) 0 else 2
            addInstructions(
                index + 1,
                """
                    invoke-virtual { p0 }, Lcom/android/volley/Request;->getUrl()Ljava/lang/String;
                    move-result-object v$scratchRegister
                    invoke-static { v$bodyRegister, v$scratchRegister }, $EXTENSION_CLASS_DESCRIPTOR->convert(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$bodyRegister
                """,
            )
        }

        // External links: MainActivity must not open r/random as a plain listing.
        mainActivityOnCreateMethodMatch.method.apply {
            val instructions = implementation!!.instructions.toList()
            val removeExtra = instructions.indexOfFirst {
                it.opcode == Opcode.INVOKE_VIRTUAL &&
                    it.getReference<MethodReference>()!!.name == "removeExtra"
            }
            if (removeExtra < 0) throw PatchException("removeExtra not found in MainActivity.onCreate")
            val check = (removeExtra + 1 until instructions.size).firstOrNull { i ->
                val instruction = instructions[i]
                instruction.opcode == Opcode.INVOKE_STATIC &&
                    instruction.getReference<MethodReference>()!!.let { reference ->
                        reference.returnType == "Z" && reference.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/String;")
                    } &&
                    instructions[i + 1].opcode == Opcode.MOVE_RESULT
            } ?: throw PatchException("subreddit-link check not found in MainActivity.onCreate")
            val link = getInstruction<FiveRegisterInstruction>(check).registerC
            val result = getInstruction<OneRegisterInstruction>(check + 1).registerA
            addInstructions(
                check + 2,
                """
                    invoke-static { v$result, v$link }, $EXTENSION_CLASS_DESCRIPTOR->isListableSubredditLink(ZLjava/lang/String;)Z
                    move-result v$result
                """,
            )
        }

        openSubredditLinkMethodMatch.method.apply {
            // r/randnsfw (Reddit's name) -> random_nsfw (Sync's), right before the name is compared.
            val compareIndex = implementation!!.instructions.indexOfFirst {
                it.opcode == Opcode.CONST_STRING &&
                    it.getReference<StringReference>()!!
                        .string == "random_nsfw"
            }
            val nameRegister = getInstruction<OneRegisterInstruction>(compareIndex - 1).also {
                if (implementation!!.instructions.elementAt(compareIndex - 1).opcode != Opcode.MOVE_RESULT_OBJECT) {
                    throw PatchException("subreddit name not found before random_nsfw")
                }
            }.registerA
            addInstructions(
                compareIndex,
                """
                    invoke-static { v$nameRegister }, $EXTENSION_CLASS_DESCRIPTOR->normalizeSubreddit(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$nameRegister
                """,
            )

            // A random link with a non-Sync context (IntentActivity) is reopened in MainActivity.
            addInstructionsWithLabels(
                0,
                """
                    invoke-static { p0, p1 }, $EXTENSION_CLASS_DESCRIPTOR->openRandomInSync(Landroid/content/Context;Ljava/lang/String;)Z
                    move-result v0
                    if-eqz v0, :continue
                    return-void
                """,
                ExternalLabel("continue", getInstruction(0)),
            )
        }
    }
}
