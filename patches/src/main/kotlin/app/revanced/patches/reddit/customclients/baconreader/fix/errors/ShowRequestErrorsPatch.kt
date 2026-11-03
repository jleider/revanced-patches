package app.revanced.patches.reddit.customclients.baconreader.fix.errors

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod.Companion.toMutable
import app.revanced.patcher.classDef
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation

@Suppress("unused")
val showRequestErrorsPatch = bytecodePatch(
    name = "Show request errors",
    description = "Shows the actual error when a Reddit request fails, " +
        "instead of the message saying that BaconReader stopped working in July 2023.",
) {
    compatibleWith(
        "com.onelouder.baconreader",
        "com.onelouder.baconreader.premium",
    )

    apply {
        // Every Reddit request catches its IOException and throws a new one with the 2023 farewell message.
        // The app's own errors are already useful ("reddit: 404 Not Found", timeouts, parse errors):
        // rebuild the method without that try block, so they reach the screen.
        val method = redditRequestRunTaskMethod
        val implementation = method.implementation!!
        val tryBlocks = implementation.tryBlocks.filterNot { tryBlock ->
            tryBlock.exceptionHandlers.any { it.exceptionType == "Ljava/io/IOException;" }
        }
        if (tryBlocks.size != implementation.tryBlocks.size - 1) {
            throw PatchException("Expected one IOException handler in ${method.definingClass}->runTask")
        }

        val rebuilt = ImmutableMethod(
            method.definingClass,
            method.name,
            method.parameters,
            method.returnType,
            method.accessFlags,
            method.annotations,
            method.hiddenApiRestrictions,
            ImmutableMethodImplementation(
                implementation.registerCount,
                implementation.instructions,
                tryBlocks,
                implementation.debugItems,
            ),
        ).toMutable()

        method.classDef.methods.apply {
            remove(method)
            add(rebuilt)
        }
    }
}
