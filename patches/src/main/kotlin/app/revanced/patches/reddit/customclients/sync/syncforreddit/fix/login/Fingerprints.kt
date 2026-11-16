package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.login

import app.revanced.patcher.*
import app.revanced.patcher.patch.BytecodePatchContext

// OAuthLoginHelper: builds the authorize.compact URL (obfuscated w8.b.d(Context) in v23.06.30).
internal val BytecodePatchContext.buildAuthorizeUrlMethodMatch by composingFirstMethod {
    returnType("Ljava/lang/String;")
    instructions(string { contains("authorize.compact?client_id") })
    custom { immutableClassDef.sourceFile == "OAuthLoginHelper.java" }
}

// OAuthLoginFragment's WebChromeClient (w8.a$a in v23.06.30).
internal val BytecodePatchContext.loginOnProgressChangedMethodMatch by composingFirstMethod {
    name("onProgressChanged")
    parameterTypes("Landroid/webkit/WebView;", "I")
    custom { immutableClassDef.sourceFile == "OAuthLoginFragment.java" }
}
