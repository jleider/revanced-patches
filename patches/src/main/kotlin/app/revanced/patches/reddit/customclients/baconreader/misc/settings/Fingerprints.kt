package app.revanced.patches.reddit.customclients.baconreader.misc.settings

import app.revanced.patcher.allOf
import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.definingClass
import app.revanced.patcher.field
import app.revanced.patcher.gettingFirstMethodDeclaratively
import app.revanced.patcher.instructions
import app.revanced.patcher.invoke
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType
import com.android.tools.smali.dexlib2.Opcode

internal val BytecodePatchContext.googleApiActivityOnCreateMethod by gettingFirstMethodDeclaratively {
    definingClass("Lcom/google/android/gms/common/api/GoogleApiActivity;")
    name("onCreate")
    returnType("V")
    parameterTypes("Landroid/os/Bundle;")
}

/** Builds the settings menu from the titles of the app's sections (sectionTitles). */
internal val BytecodePatchContext.settingsMenuInitAdapterMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/SettingsMenuFragment;")
    name("initAdapter")
    returnType("V")
    parameterTypes()
    instructions(allOf(Opcode.SGET_OBJECT(), field("sectionTitles")))
}

internal val BytecodePatchContext.settingsActivityOpenFragmentMethod by gettingFirstMethodDeclaratively {
    definingClass("Lcom/onelouder/baconreader/SettingsActivity;")
    name("openFragment")
    returnType("V")
    parameterTypes("I")
}
