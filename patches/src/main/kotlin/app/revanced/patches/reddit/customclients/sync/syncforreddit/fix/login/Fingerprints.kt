package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.login

import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.custom
import app.revanced.patcher.immutableClassDef
import app.revanced.patcher.instructions
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType
import app.revanced.patcher.string

// OAuthLoginHelper: builds the authorize.compact URL.
internal val BytecodePatchContext.buildAuthorizeUrlMethodMatch by composingFirstMethod {
    returnType("Ljava/lang/String;")
    instructions(string { contains("authorize.compact?client_id") })
    custom { immutableClassDef.sourceFile == "OAuthLoginHelper.java" }
}

// OAuthLoginFragment's WebChromeClient.
internal val BytecodePatchContext.loginOnProgressChangedMethodMatch by composingFirstMethod {
    name("onProgressChanged")
    parameterTypes("Landroid/webkit/WebView;", "I")
    custom { immutableClassDef.sourceFile == "OAuthLoginFragment.java" }
}
