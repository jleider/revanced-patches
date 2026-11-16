package app.revanced.patches.reddit.customclients.sync.syncforreddit.misc.https

import app.revanced.patcher.*
import app.revanced.patcher.extensions.*
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch

// OkHttp is not obfuscated in Sync; its real class name and source file are stable.
internal val BytecodePatchContext.okHttpBuildMatch by composingFirstMethod {
    name("build")
    returnType("Lokhttp3/OkHttpClient;")
    parameterTypes()
    custom { immutableClassDef.type == "Lokhttp3/OkHttpClient\$Builder;" && immutableClassDef.sourceFile == "OkHttpClient.kt" }
}

@Suppress("unused")
val upgradeToHttpsPatch = bytecodePatch(
    name = "Upgrade connections to HTTPS",
    description = "Requests plain http:// URLs over https:// on every OkHttp client (feeds, images, video, " +
        "link previews), falling back to http only for hosts whose HTTPS doesn't connect.",
    use = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith("com.laurencedawson.reddit_sync", "com.laurencedawson.reddit_sync.pro", "com.laurencedawson.reddit_sync.dev")
    apply {
        okHttpBuildMatch.method.addInstructions(0, """
            invoke-static { p0 }, Lapp/revanced/extension/syncforreddit/HttpsUpgradeInterceptor;->install(Lokhttp3/OkHttpClient${'$'}Builder;)Lokhttp3/OkHttpClient${'$'}Builder;
        """)
    }
}
