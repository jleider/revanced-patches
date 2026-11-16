package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.imgur

import app.revanced.patcher.*
import app.revanced.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.AccessFlags

internal val BytecodePatchContext.singleUrlMethodMatch by composingFirstMethod {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
    parameterTypes("Ljava/lang/String;")
    returnType("Ljava/lang/String;")
    custom { immutableClassDef.sourceFile == "ImgurSingleImageRequest.java" }
}

internal val BytecodePatchContext.albumUrlMethodMatch by composingFirstMethod {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
    parameterTypes("Ljava/lang/String;")
    returnType("Ljava/lang/String;")
    custom { immutableClassDef.sourceFile == "ImgurGalleryRequest.java" }
}

internal val BytecodePatchContext.albumHeadersMethodMatch by composingFirstMethod {
    name("getHeaders")
    instructions(string { contains("sync_for_reddit") })
    custom { immutableClassDef.sourceFile == "ImgurGalleryRequest.java" }
}

internal val BytecodePatchContext.queueAddMethodMatch by composingFirstMethod {
    name("add")
    instructions(string { contains("add-to-queue") })
    custom { immutableClassDef.sourceFile == "RequestQueue.java" }
}

internal val BytecodePatchContext.albumParseMethodMatch by composingFirstMethod {
    name("parseNetworkResponse")
    parameterTypes("Lcom/android/volley/NetworkResponse;")
    custom { immutableClassDef.sourceFile == "ImgurGalleryRequest.java" }
}
