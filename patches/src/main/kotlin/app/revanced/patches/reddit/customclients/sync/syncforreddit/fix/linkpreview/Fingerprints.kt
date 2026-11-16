package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.linkpreview

import app.revanced.patcher.*
import app.revanced.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// OkHttpHelper.a(): lazily builds the OkHttpClient Glide loads every image through (a8.a.a in v23.06.30).
// The other no-arg getters only return fields; this is the one that calls Builder.addInterceptor.
internal val BytecodePatchContext.imageClientMethodMatch by composingFirstMethod {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
    returnType("Lokhttp3/OkHttpClient;")
    parameterTypes()
    custom {
        immutableClassDef.sourceFile == "OkHttpHelper.java" &&
            implementation?.instructions?.any {
                ((it as? ReferenceInstruction)?.reference as? MethodReference)?.name == "addInterceptor"
            } == true
    }
}
