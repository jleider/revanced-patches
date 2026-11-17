package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.gallery

import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.custom
import app.revanced.patcher.immutableClassDef
import app.revanced.patcher.instructions
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType
import app.revanced.patcher.string

// Builds the /api/info.json URL.
internal val BytecodePatchContext.galleryUrlMethodMatch by composingFirstMethod {
    returnType("Ljava/lang/String;")
    parameterTypes("Ljava/lang/String;")
    instructions(string { contains("api/info.json?id=t3_") })
    custom { immutableClassDef.sourceFile == "OAuthGalleryRequest.java" }
}

// OAuthGalleryRequest.parseNetworkResponse(NetworkResponse).
internal val BytecodePatchContext.galleryParseMethodMatch by composingFirstMethod {
    name("parseNetworkResponse")
    returnType("Lcom/android/volley/Response;")
    parameterTypes("Lcom/android/volley/NetworkResponse;")
    custom { immutableClassDef.sourceFile == "OAuthGalleryRequest.java" }
}

// MultiImageActivity.onDestroy(): writes the last viewed "position" into the cached gallery response.
internal val BytecodePatchContext.galleryActivityOnDestroyMethodMatch by composingFirstMethod {
    name("onDestroy")
    returnType("V")
    parameterTypes()
    custom { immutableClassDef.sourceFile == "MultiImageActivity.java" }
}
