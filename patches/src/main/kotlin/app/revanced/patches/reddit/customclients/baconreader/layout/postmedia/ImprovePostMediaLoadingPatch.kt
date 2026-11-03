package app.revanced.patches.reddit.customclients.baconreader.layout.postmedia

import app.revanced.patcher.extensions.ExternalLabel
import app.revanced.patcher.firstClassDef
import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.all.misc.resources.addResources
import app.revanced.patches.all.misc.resources.addResourcesPatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch
import app.revanced.patches.reddit.customclients.baconreader.misc.settings.PreferenceScreen
import app.revanced.patches.reddit.customclients.baconreader.misc.settings.settingsPatch
import app.revanced.patches.shared.misc.settings.preference.InputType
import app.revanced.patches.shared.misc.settings.preference.SwitchPreference
import app.revanced.patches.shared.misc.settings.preference.TextPreference
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/baconreader/postmedia/PostMediaPatch;"
private const val GALLERY_EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/baconreader/postmedia/RedditGalleryPatch;"

/** The OkHttp callback of PostMediaHandler.loadGifContent, used when Glide could not load the image. */
private const val GIF_FALLBACK_CALLBACK_CLASS_DESCRIPTOR = "Lcom/onelouder/baconreader/PostMediaHandler\$4\$1;"

/** Its lambdas run when the response is not successful (4), no bitmap could be made (2), or rendering threw (3). */
private val GIF_FALLBACK_FAILURES = listOf(2, 3, 4)

@Suppress("unused")
val improvePostMediaLoadingPatch = bytecodePatch(
    name = "Improve post media loading",
    description = "Shows a still preview with a progress bar and the downloaded size while post media " +
        "and gallery images load, uses small previews for the thumbnails of galleries, " +
        "shows a message instead of a broken page when a RedGIFs video was deleted, " +
        "and adds options for which link post pages run JavaScript.",
) {
    dependsOn(sharedExtensionPatch, settingsPatch, addResourcesPatch)

    compatibleWith(
        "com.onelouder.baconreader"("6.1.4"),
        "com.onelouder.baconreader.premium"("6.1.4"),
    )

    apply {
        addResources("baconreader", "layout.postmedia.improvePostMediaLoadingPatch")

        // Images and GIFs: when Glide fails, the app downloads the file with OkHttp. If that fails too, it only
        // logs it and keeps the loading page: show the loading bar as failed.
        firstClassDef(GIF_FALLBACK_CALLBACK_CLASS_DESCRIPTOR).methods.filter { method ->
            method.name == "onFailure" || GIF_FALLBACK_FAILURES.any { method.name.startsWith("lambda\$onResponse\$$it\$") }
        }.also {
            if (it.size != GIF_FALLBACK_FAILURES.size + 1) {
                throw PatchException("Expected ${GIF_FALLBACK_FAILURES.size + 1} failure methods, found ${it.size}")
            }
        }.forEach { method ->
            method.addInstruction(
                0,
                "invoke-static { p0 }, $EXTENSION_CLASS_DESCRIPTOR->onImageLoadFailed(Ljava/lang/Object;)V",
            )
        }

        // Loading overlay (still preview, size, progress), before the app resolves the media.
        postMediaHandlerStartMethod.addInstruction(
            0,
            "invoke-static { p0 }, $EXTENSION_CLASS_DESCRIPTOR->onStart(Ljava/lang/Object;)V",
        )

        // GIFs: use the app's cache loader, which reports the download progress, instead of Glide.
        loadGifContentMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static { p0, p1 }, $EXTENSION_CLASS_DESCRIPTOR->loadGifWithProgress(Ljava/lang/Object;Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :original
                return-void
            """,
            ExternalLabel("original", loadGifContentMethod.getInstruction(0)),
        )

        // Download progress of the cache loader.
        onReceivedDataMethod.addInstruction(
            0,
            "invoke-static { p0, p1, p2 }, $EXTENSION_CLASS_DESCRIPTOR->onProgress(Ljava/lang/Object;II)V",
        )

        // Galleries: the thumbnail strip loads the full images instead of the previews (see the extension).
        // Use a preview big enough for the strip, the preview itself as thumbnail, and download the image
        // shown with a progress bar.
        getGalleryImagesMethodMatch.method.apply {
            val getIndex = getGalleryImagesMethodMatch[1]
            val get = getInstruction<FiveRegisterInstruction>(getIndex)
            addInstructions(
                getIndex,
                """
                    invoke-static { v${get.registerC} }, $GALLERY_EXTENSION_CLASS_DESCRIPTOR->previewIndex(Ljava/util/List;)I
                    move-result v${get.registerD}
                """,
            )
        }

        redditImageThumbnailMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static { p0 }, $GALLERY_EXTENSION_CLASS_DESCRIPTOR->thumbnail(Ljava/lang/Object;)Ljava/lang/String;
                move-result-object v0
                if-eqz v0, :original
                return-object v0
            """,
            ExternalLabel("original", redditImageThumbnailMethod.getInstruction(0)),
        )

        galleryGlideMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static { p0, p1 }, $GALLERY_EXTENSION_CLASS_DESCRIPTOR->loadImage(Ljava/lang/Object;Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :original
                return-void
            """,
            ExternalLabel("original", galleryGlideMethod.getInstruction(0)),
        )

        // When the RedGIFs API answers with an error, the app shows RedGIFs' web page instead, which needs
        // JavaScript. Remember the error, so a deleted or missing video shows a message instead.
        redgifsResponseMethodMatch.method.apply {
            val messageIndex = redgifsResponseMethodMatch[0]
            val responseRegister = getInstruction<FiveRegisterInstruction>(messageIndex).registerC
            addInstruction(
                messageIndex,
                "invoke-static { v$responseRegister }, $EXTENSION_CLASS_DESCRIPTOR->onRedgifsError(Ljava/lang/Object;)V",
            )
        }

        redgifsCancelMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static { }, $EXTENSION_CLASS_DESCRIPTOR->redgifsErrorMessage()Ljava/lang/String;
                move-result-object v0
                if-eqz v0, :original
                iget-object v1, p0, Lcom/onelouder/baconreader/imageutils/ImageHelper${'$'}4;->val${'$'}listener:Lcom/onelouder/baconreader/imageutils/ImageHelper${'$'}ImageHelperListener;
                invoke-virtual { v1, v0 }, Lcom/onelouder/baconreader/imageutils/ImageHelper${'$'}ImageHelperListener;->onCancel(Ljava/lang/String;)V
                return-void
            """,
            ExternalLabel("original", redgifsCancelMethod.getInstruction(0)),
        )

        PreferenceScreen.LINKS.addPreferences(
            SwitchPreference("revanced_baconreader_link_preview_javascript"),
            TextPreference("revanced_baconreader_link_preview_javascript_blocked", inputType = InputType.TEXT_MULTI_LINE),
            TextPreference("revanced_baconreader_link_preview_javascript_allowed", inputType = InputType.TEXT_MULTI_LINE),
        )

        // The web view of link posts runs JavaScript only for a list of sites from before most of today's web
        // needed it (several of them shut down), so many pages show nothing. The settings decide instead.
        enableJavascriptMethodMatch.method.apply {
            // Right after the app sets its default (the setting of its own browser); its list is skipped.
            val defaultIndex = enableJavascriptMethodMatch[0]
            addInstructionsWithLabels(
                defaultIndex + 1,
                """
                    move-object/from16 v1, p1
                    invoke-static { v1 }, $EXTENSION_CLASS_DESCRIPTOR->needsJavascript(Ljava/lang/String;)Z
                    move-result v1
                    if-eqz v1, :done
                    move-object/from16 v1, p0
                    iget-object v1, v1, Lcom/onelouder/baconreader/BrowserBase;->webView:Lcom/onelouder/baconreader/PSWebView;
                    invoke-virtual { v1 }, Lcom/onelouder/baconreader/PSWebView;->getSettings()Landroid/webkit/WebSettings;
                    move-result-object v1
                    const/4 v2, 0x1
                    invoke-virtual { v1, v2 }, Landroid/webkit/WebSettings;->setJavaScriptEnabled(Z)V
                    :done
                    return-void
                """,
            )
        }
    }
}
