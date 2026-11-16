package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.morecomments

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION = "Lapp/revanced/extension/syncforreddit/FixMoreCommentsPatch;"

@Suppress("unused")
val fixMoreCommentsPatch = bytecodePatch(
    name = "Fix load more comments",
    description = "Loads additional comments through comment permalinks instead of the blocked morechildren API.",
    use = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )
    apply {
        // v7 is not yet assigned in V(Post), .locals 12. p1 is still the more
        // Post here. Preserve its parent alongside children in the GET-only map.
        moreCallerMatch.method.apply {
            val index = implementation!!.instructions.indexOfFirst {
                it.opcode == Opcode.INVOKE_VIRTUAL &&
                    ((it as ReferenceInstruction).reference as MethodReference).name == "u"
            }
            if (index < 0) throw PatchException("More children getter not found")
            val postClass = (getInstruction<ReferenceInstruction>(index).reference as MethodReference).definingClass
            val result = getInstruction<OneRegisterInstruction>(index + 1).registerA
            addInstructions(index + 2, """
                invoke-virtual { p1 }, $postClass->t0()Ljava/lang/String;
                move-result-object v7
                invoke-static { v$result, v7 }, $EXTENSION->encodeChildren(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
                move-result-object v$result
            """)
        }
        moreConstructorMatch.method.apply {
            // .locals 9: v0/v1/v2 are scratch until the super arguments are set;
            // v3 is the URL. The original const/4 v2,1 precedes these moves.
            val superIndex = implementation!!.instructions.indexOfFirst {
                it.opcode == Opcode.INVOKE_DIRECT_RANGE &&
                    ((it as ReferenceInstruction).reference as MethodReference).let { ref ->
                        ref.name == "<init>" && ref.definingClass != "Ljava/lang/StringBuilder;"
                    }
            }
            if (superIndex < 0) throw PatchException("More request super constructor not found")
            val methodIndex = (0 until superIndex).last {
                getInstruction(it).opcode == Opcode.CONST_4
            }
            addInstructions(methodIndex + 1, """
                move-object/from16 v0, p3
                move-object/from16 v1, p5
                move-object/from16 v2, p8
                invoke-static { v0, v1, v2 }, $EXTENSION->buildUrl(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
                move-result-object v3
                const/4 v2, 0x0
            """)
        }
        moreParseMatch.method.apply {
            // .locals 18: v7 is unused until the iteration. v1 already holds
            // this (p0 is too high for a non-range invoke). Keep all live locals.
            val index = implementation!!.instructions.indexOfFirst {
                it.opcode == Opcode.INVOKE_DIRECT &&
                    ((it as ReferenceInstruction).reference as MethodReference).let { ref ->
                        ref.definingClass == "Ljava/lang/String;" && ref.name == "<init>" &&
                            ref.parameterTypes.map(CharSequence::toString) == listOf("[B")
                    }
            }
            if (index < 0) throw PatchException("More response String constructor not found")
            val stringRegister = getInstruction<FiveRegisterInstruction>(index).registerC
            addInstructions(index + 1, """
                iget-object v7, v1, Lx8/a;->s:Ljava/util/Map;
                invoke-static { v$stringRegister, v7 }, $EXTENSION->convert(Ljava/lang/String;Ljava/util/Map;)Ljava/lang/String;
                move-result-object v$stringRegister
            """)
        }
    }
}
