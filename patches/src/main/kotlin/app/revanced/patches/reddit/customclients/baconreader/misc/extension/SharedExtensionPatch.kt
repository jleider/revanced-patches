package app.revanced.patches.reddit.customclients.baconreader.misc.extension

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.firstClassDef
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.all.misc.resources.addResources
import app.revanced.patches.all.misc.resources.addResourcesPatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.hooks.applicationOnCreateMethod
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.hooks.initHook
import app.revanced.patches.shared.misc.extension.sharedExtensionPatch

val sharedExtensionPatch = bytecodePatch {
    dependsOn(sharedExtensionPatch("baconreader", initHook), addResourcesPatch)

    apply {
        addResources("baconreader", "misc.extension.sharedExtensionPatch")

        // Errors of the patches are shown in a dialog on the screen on top, which the extension follows.
        applicationOnCreateMethod.addInstruction(
            0,
            "invoke-static { p0 }, Lapp/revanced/extension/baconreader/errors/ErrorDialog;->" +
                "init(Landroid/app/Application;)V",
        )

        // Also the errors of the shared ReVanced code (settings screen, ...): the dialog instead of a toast.
        firstClassDef("Lapp/revanced/extension/shared/Logger;").methods.first { method ->
            method.name == "printException" && method.parameterTypes.size == 2
        }.addInstructions(
            0,
            """
                invoke-static { p0, p1 }, Lapp/revanced/extension/baconreader/errors/ErrorDialog;->printException(Lapp/revanced/extension/shared/Logger${'$'}LogMessage;Ljava/lang/Throwable;)V
                return-void
            """,
        )
    }
}
