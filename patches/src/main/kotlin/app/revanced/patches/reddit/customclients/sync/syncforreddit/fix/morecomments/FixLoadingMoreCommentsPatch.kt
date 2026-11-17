package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.morecomments

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.revanced.util.getReference
import app.revanced.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/syncforreddit/FixMoreCommentsPatch;"

@Suppress("unused")
val fixLoadingMoreCommentsPatch = bytecodePatch(
    name = "Fix loading more comments",
    description = "Fixes loading more comments.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    apply {
        // Resolve obfuscated Post getters and the request's params field from stable anchors
        // (column names in AutoValue_Post's serializer, RedditRequest.getParams) instead of names.
        fun getterFor(column: String): MethodReference {
            val serializer = serializePostMethodMatch.method
            val instructions = serializer.implementation!!.instructions.toList()
            val key = instructions.indexOfFirst {
                it.getReference<StringReference>()?.string == column
            }
            if (key < 0) throw PatchException("Column $column not found in Post serializer")
            return (key - 1 downTo 0).firstNotNullOfOrNull { i ->
                instructions[i].getReference<MethodReference>()
                    ?.takeIf { it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/String;" }
            } ?: throw PatchException("Getter for $column not found")
        }
        val childrenGetter = getterFor("commentMoreIds")
        val parentGetter = getterFor("parent_id")
        val paramsField = getRequestParamsMethodMatch.method.implementation!!.instructions.firstNotNullOfOrNull {
            if (it.opcode == Opcode.IGET_OBJECT) it.getReference<FieldReference>()!! else null
        } ?: throw PatchException("RedditRequest params field not found")

        // v7 is not yet assigned in V(Post), .locals 12. p1 is still the more
        // Post here. Preserve its parent alongside children in the GET-only map.
        // Tripwire: the free-register analysis below assumes these exact frame sizes.
        fun com.android.tools.smali.dexlib2.iface.Method.locals() =
            implementation!!.registerCount - parameterTypes.size -
                (if (com.android.tools.smali.dexlib2.AccessFlags.STATIC.isSet(accessFlags)) 0 else 1)

        loadMoreCommentsMethodMatch.method.apply {
            check(locals() == 12) { "CommentsPresenterImpl more-loader frame changed (${locals()} locals)" }
            val index = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_VIRTUAL &&
                    getReference<MethodReference>()!!.let { reference ->
                        reference.name == childrenGetter.name && reference.parameterTypes.isEmpty() &&
                            reference.returnType == "Ljava/lang/String;"
                    }
            }
            val postClass = getInstruction(index).getReference<MethodReference>()!!.definingClass
            val result = getInstruction<OneRegisterInstruction>(index + 1).registerA
            addInstructions(
                index + 2,
                """
                    invoke-virtual { p1 }, $postClass->${parentGetter.name}()Ljava/lang/String;
                    move-result-object v7
                    invoke-static { v$result, v7 }, $EXTENSION_CLASS_DESCRIPTOR->encodeChildren(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$result
                """,
            )
        }
        moreRequestConstructorMethodMatch.method.apply {
            check(locals() == 9) { "OAuthCommentsMoreRequest constructor frame changed (${locals()} locals)" }
            // .locals 9: v0/v1/v2 are scratch until the super arguments are set;
            // v3 is the URL. The original const/4 v2,1 precedes these moves.
            val superIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_DIRECT_RANGE &&
                    getReference<MethodReference>()!!.let { reference ->
                        reference.name == "<init>" && reference.definingClass != "Ljava/lang/StringBuilder;"
                    }
            }
            val methodIndex = (0 until superIndex).last {
                getInstruction(it).opcode == Opcode.CONST_4
            }
            addInstructions(
                methodIndex + 1,
                """
                    move-object/from16 v0, p3
                    move-object/from16 v1, p5
                    move-object/from16 v2, p8
                    invoke-static { v0, v1, v2 }, $EXTENSION_CLASS_DESCRIPTOR->buildUrl(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v3
                    const/4 v2, 0x0
                """,
            )
        }
        parseMoreCommentsMethodMatch.method.apply {
            check(locals() == 18) { "OAuthCommentsMoreRequest parser frame changed (${locals()} locals)" }
            check(implementation!!.instructions.first().let {
                it.opcode == Opcode.MOVE_OBJECT_FROM16 && (it as OneRegisterInstruction).registerA == 1
            }) { "Parser no longer starts with move-object/from16 v1, p0" }
            // .locals 18: v7 is unused until the iteration. v1 already holds
            // this (p0 is too high for a non-range invoke). Keep all live locals.
            val index = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_DIRECT &&
                    getReference<MethodReference>()!!.let { reference ->
                        reference.definingClass == "Ljava/lang/String;" && reference.name == "<init>" &&
                            reference.parameterTypes.map(CharSequence::toString) == listOf("[B")
                    }
            }
            val stringRegister = getInstruction<FiveRegisterInstruction>(index).registerC
            addInstructions(
                index + 1,
                """
                    iget-object v7, v1, ${paramsField.definingClass}->${paramsField.name}:${paramsField.type}
                    invoke-static { v$stringRegister, v7 }, $EXTENSION_CLASS_DESCRIPTOR->convert(Ljava/lang/String;Ljava/util/Map;)Ljava/lang/String;
                    move-result-object v$stringRegister
                """,
            )
        }
    }
}
