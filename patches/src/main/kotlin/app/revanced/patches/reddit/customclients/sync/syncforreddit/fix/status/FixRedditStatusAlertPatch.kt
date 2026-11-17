package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.status

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/syncforreddit/FixRedditStatusPatch;"

@Suppress("unused")
val fixRedditStatusAlertPatch = bytecodePatch(
    name = "Fix Reddit status alert",
    description = "Shows the Reddit outage alert only for incidents that can affect Sync, with the incident's details. " +
        "An incident is shown again after a new update or six hours.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    apply {
        // Replace the parser: Response.success(Boolean.valueOf(healthy(new String(data))), cacheHeaders).
        statusParseMethodMatch.method.addInstructions(
            0,
            """
                iget-object v0, p1, Lcom/android/volley/NetworkResponse;->data:[B
                new-instance v1, Ljava/lang/String;
                invoke-direct { v1, v0 }, Ljava/lang/String;-><init>([B)V
                invoke-static { v1 }, $EXTENSION_CLASS_DESCRIPTOR->healthy(Ljava/lang/String;)Z
                move-result v0
                invoke-static { v0 }, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
                move-result-object v0
                invoke-static { p1 }, Lcom/android/volley/toolbox/HttpHeaderParser;->parseCacheHeaders(Lcom/android/volley/NetworkResponse;)Lcom/android/volley/Cache${'$'}Entry;
                move-result-object v1
                invoke-static { v0, v1 }, Lcom/android/volley/Response;->success(Ljava/lang/Object;Lcom/android/volley/Cache${'$'}Entry;)Lcom/android/volley/Response;
                move-result-object v0
                return-object v0
            """,
        )

        statusMessageMethodMatch.method.apply {
            val index = implementation!!.instructions.indexOfLast { it.opcode == Opcode.RETURN_OBJECT }
            val register = getInstruction<OneRegisterInstruction>(index).registerA
            addInstructions(
                index,
                """
                    invoke-static { v$register }, $EXTENSION_CLASS_DESCRIPTOR->message(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$register
                """,
            )
        }
    }
}
