package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.gallery

import app.revanced.patcher.*
import app.revanced.patcher.patch.BytecodePatchContext

// OAuthGalleryRequest.b(String): builds the /api/info.json URL (y8.q.b in v23.06.30).
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
