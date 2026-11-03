package app.revanced.patches.reddit.customclients.baconreader.layout.postmedia

import app.revanced.patcher.allOf
import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.definingClass
import app.revanced.patcher.field
import app.revanced.patcher.gettingFirstMethodDeclaratively
import app.revanced.patcher.instructions
import app.revanced.patcher.invoke
import app.revanced.patcher.method
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType
import com.android.tools.smali.dexlib2.Opcode

private const val POST_MEDIA_HANDLER_CLASS_DESCRIPTOR = "Lcom/onelouder/baconreader/PostMediaHandler;"

internal val BytecodePatchContext.postMediaHandlerStartMethod by gettingFirstMethodDeclaratively {
    definingClass(POST_MEDIA_HANDLER_CLASS_DESCRIPTOR)
    name("start")
    returnType("V")
    parameterTypes()
}

internal val BytecodePatchContext.loadGifContentMethod by gettingFirstMethodDeclaratively {
    definingClass(POST_MEDIA_HANDLER_CLASS_DESCRIPTOR)
    name("loadGifContent")
    returnType("V")
    parameterTypes("Ljava/lang/String;")
}

internal val BytecodePatchContext.onReceivedDataMethod by gettingFirstMethodDeclaratively {
    definingClass("Lcom/onelouder/baconreader/PostMediaHandler\$5;")
    name("onReceivedData")
    parameterTypes("I", "I")
}

/** Handles the answer of the RedGIFs API, and cancels with Response.message() when it is an error. */
internal val BytecodePatchContext.redgifsResponseMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/media/gfycat/RedgifHandler\$resolveAsync\$1\$1\$1\$onResponse\$1;")
    name("invokeSuspend")
    instructions(
        allOf(Opcode.INVOKE_VIRTUAL(), method { definingClass == "Lretrofit2/Response;" && name == "message" }),
    )
}

internal val BytecodePatchContext.redgifsCancelMethod by gettingFirstMethodDeclaratively {
    definingClass("Lcom/onelouder/baconreader/imageutils/ImageHelper\$4;")
    name("onCancel")
    returnType("V")
    parameterTypes("Ljava/lang/String;")
}

private const val REDDIT_GALLERY_VIEWER_CLASS_DESCRIPTOR =
    "Lcom/onelouder/baconreader/reddit_gallery/RedditGalleryViewer;"

/** Picks the preview of each gallery image: RedditMediaData.p (the previews), then List.get(index). */
internal val BytecodePatchContext.getGalleryImagesMethodMatch by composingFirstMethod {
    definingClass(REDDIT_GALLERY_VIEWER_CLASS_DESCRIPTOR)
    name("getGalleryImages")
    returnType("Ljava/util/List;")
    parameterTypes("Lcom/onelouder/baconreader/reddit/Link;")
    instructions(
        allOf(
            Opcode.IGET_OBJECT(),
            field { definingClass == "Lcom/onelouder/baconreader/reddit_gallery/RedditMediaData;" && name == "p" },
        ),
        allOf(Opcode.INVOKE_INTERFACE(), method { definingClass == "Ljava/util/List;" && name == "get" }),
    )
}

internal val BytecodePatchContext.galleryGlideMethod by gettingFirstMethodDeclaratively {
    definingClass(REDDIT_GALLERY_VIEWER_CLASS_DESCRIPTOR)
    name("glide")
    returnType("V")
    parameterTypes("Lcom/onelouder/baconreader/reddit_gallery/RedditImage;")
}

internal val BytecodePatchContext.redditImageThumbnailMethod by gettingFirstMethodDeclaratively {
    definingClass("Lcom/onelouder/baconreader/reddit_gallery/RedditImage;")
    name("thumbnail")
    returnType("Ljava/lang/String;")
    parameterTypes("Ljava/lang/String;")
}

/** Turns JavaScript on in the web view of link posts, for a few sites only, after setting its default. */
internal val BytecodePatchContext.enableJavascriptMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/BrowserBase;")
    name("enableJavascript")
    returnType("V")
    parameterTypes("Ljava/lang/String;")
    instructions(allOf(Opcode.INVOKE_VIRTUAL(), method("setJavaScriptEnabled")))
}
