package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.deadservices

import app.revanced.patcher.*
import app.revanced.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

// CheckDeviantArtRequest(String, Listener, ErrorListener) (r8.a in v23.06.30).
internal val BytecodePatchContext.deviantArtConstructorMatch by composingFirstMethod {
    name("<init>")
    instructions(string { equals("http://backend.deviantart.com/oembed?url=") })
    custom { immutableClassDef.sourceFile == "CheckDeviantArtRequest.java" }
}

// CommentMoreDialogBottomSheet: builds the comment action list, including "Restore comment" (t9.s.A4).
internal val BytecodePatchContext.commentActionsMethodMatch by composingFirstMethod {
    returnType("V")
    parameterTypes()
    instructions(string { equals("Restore comment") })
    custom { immutableClassDef.sourceFile == "CommentMoreDialogBottomSheet.java" }
}

internal fun Instruction.stringValue() =
    ((this as? ReferenceInstruction)?.reference as? StringReference)?.string
