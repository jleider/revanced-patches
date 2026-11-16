package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.random

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.multisub.redditRequestConstructorMatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/syncforreddit/FixRandomSubredditPatch;"

@Suppress("unused")
val fixRandomSubredditPatch = bytecodePatch(
    name = "Fix random subreddit",
    description = "Picks a random subreddit from Reddit's popular list, because r/random was removed.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    apply {
        // RandomRequest extends RedditRequest; rewrite r/random(nsfw).json in the base constructor.
        redditRequestConstructorMatch.method.addInstructions(
            0,
            """
                invoke-static { p3 }, $EXTENSION_CLASS_DESCRIPTOR->rewriteUrl(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p3
            """,
        )

        // .locals 3: right after `v1 = new String(bytes)`, v2 (the byte array) is dead and reassigned next.
        randomParseMethodMatch.method.apply {
            val index = implementation!!.instructions.indexOfFirst {
                it.opcode == Opcode.INVOKE_DIRECT &&
                    ((it as ReferenceInstruction).reference as MethodReference).let { ref ->
                        ref.definingClass == "Ljava/lang/String;" && ref.name == "<init>" &&
                            ref.parameterTypes.map(CharSequence::toString) == listOf("[B")
                    }
            }
            if (index < 0) throw PatchException("String(byte[]) not found in RandomRequest parser")
            val body = getInstruction<FiveRegisterInstruction>(index).registerC
            val scratch = if (body == 2) 0 else 2
            addInstructions(
                index + 1,
                """
                    invoke-virtual { p0 }, Lcom/android/volley/Request;->getUrl()Ljava/lang/String;
                    move-result-object v$scratch
                    invoke-static { v$body, v$scratch }, $EXTENSION_CLASS_DESCRIPTOR->convert(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$body
                """,
            )
        }
    }
}
