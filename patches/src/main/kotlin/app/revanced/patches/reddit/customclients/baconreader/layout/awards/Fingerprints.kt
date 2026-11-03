package app.revanced.patches.reddit.customclients.baconreader.layout.awards

import app.revanced.patcher.afterAtMost
import app.revanced.patcher.allOf
import app.revanced.patcher.anyOf
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

internal const val CONTROL_BAR_CLASS_DESCRIPTOR = "Lcom/onelouder/baconreader/controlbar/ControlBar;"
internal const val CONTROL_BAR_BUTTON_CLASS_DESCRIPTOR = "Lcom/onelouder/baconreader/controlbar/ControlBarButton;"

/** Adds a button to the action bar of comments and posts. */
internal val BytecodePatchContext.controlBarSetButtonMethod by gettingFirstMethodDeclaratively {
    definingClass(CONTROL_BAR_CLASS_DESCRIPTOR)
    name("setButton")
    returnType(CONTROL_BAR_BUTTON_CLASS_DESCRIPTOR)
    parameterTypes("Ljava/lang/String;")
}

/** Handles the clicks of the post bar, including the ⋮ button that shows its menu. */
internal val BytecodePatchContext.controlbarBaseOnClickMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/controlbar/ControlbarBase;")
    name("onClick")
    returnType("V")
    parameterTypes("Landroid/view/View;")
    instructions(
        allOf(
            Opcode.INVOKE_VIRTUAL(),
            method { definingClass == "Landroidx/appcompat/widget/PopupMenu;" && name == "show" },
        ),
    )
}

internal val BytecodePatchContext.hideAwardIconsMethod by gettingFirstMethodDeclaratively {
    definingClass("Lcom/onelouder/baconreader/Preferences;")
    name("hideAwardIcons")
    returnType("Z")
    parameterTypes()
}

internal val BytecodePatchContext.hideAwardIconsOnDetailViewMethod by gettingFirstMethodDeclaratively {
    definingClass("Lcom/onelouder/baconreader/Preferences;")
    name("hideAwardIconsOnDetailView")
    returnType("Z")
    parameterTypes()
}

/** Fills the settings lists, including the two "Hide Reddit Awards" settings: list.add(new Setting(id, ...)). */
internal val BytecodePatchContext.settingsAdapterInitSettingsMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/adapters/SettingsAdapter;")
    name("initSettings")
    returnType("V")
    instructions(*addSetting("HIDE_AWARD_ICONS"))
    instructions(*addSetting("HIDE_AWARD_ICONS_DETAIL_VIEW"))
}

private fun addSetting(settingId: String) = arrayOf(
    allOf(
        Opcode.SGET_OBJECT(),
        field { definingClass == "Lcom/onelouder/baconreader/adapters/SettingIds;" && name == settingId },
    ),
    afterAtMost(2, allOf(anyOf(Opcode.INVOKE_VIRTUAL(), Opcode.INVOKE_INTERFACE()), method("add"))),
)
