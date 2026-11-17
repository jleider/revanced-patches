package app.revanced.patches.reddit.customclients.sync.syncforreddit.misc.https

import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.custom
import app.revanced.patcher.immutableClassDef
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType

// OkHttp is not obfuscated in Sync; its real class name and source file are stable.
internal val BytecodePatchContext.buildOkHttpClientMethodMatch by composingFirstMethod {
    name("build")
    returnType("Lokhttp3/OkHttpClient;")
    parameterTypes()
    custom { immutableClassDef.type == "Lokhttp3/OkHttpClient\$Builder;" && immutableClassDef.sourceFile == "OkHttpClient.kt" }
}
