package app.revanced.patches.reddit.customclients.sync.syncforreddit.debug

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Development aid: routes Sync's stripped internal logger to logcat so request URLs,
 * response bodies and API errors become visible (`adb logcat -s reddit_sync RedditRequest URL`).
 * Off by default. Logs can contain tokens and account data, so don't ship it to daily-use builds.
 */
@Suppress("unused")
val enableDebugLoggingPatch = bytecodePatch(
    name = "Enable debug logging",
    description = "Enables Sync logging and WebView debugging. For development only.",
    use = false,
) {
    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    apply {
        syncApplicationOnCreateMethodMatch.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                invoke-static { v0 }, Landroid/webkit/WebView;->setWebContentsDebuggingEnabled(Z)V
            """,
        )

        val loggerMethods = syncLoggerMethodMatch.classDef.methods.filter { method ->
            AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.returnType == "V" &&
                method.parameterTypes.map { it.toString() } == listOf("Ljava/lang/String;", "Ljava/lang/String;") &&
                method.implementation?.instructions?.count() == 1
        }
        if (loggerMethods.isEmpty()) throw PatchException("No stripped (String, String) logger methods found")

        loggerMethods.forEach { method ->
            method.addInstructions(
                0,
                "invoke-static { p0, p1 }, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I",
            )
        }
    }
}
