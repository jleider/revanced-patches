package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.linkpreview

import app.revanced.patcher.accessFlags
import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.custom
import app.revanced.patcher.immutableClassDef
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType
import app.revanced.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// Lazily builds the OkHttpClient Glide loads every image through.
// The other no-arg getters only return fields; this is the one that calls Builder.addInterceptor.
internal val BytecodePatchContext.imageClientMethodMatch by composingFirstMethod {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
    returnType("Lokhttp3/OkHttpClient;")
    parameterTypes()
    custom {
        immutableClassDef.sourceFile == "OkHttpHelper.java" &&
            implementation?.instructions?.any {
                it.getReference<MethodReference>()?.name == "addInterceptor"
            } == true
    }
}
