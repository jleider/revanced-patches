package app.revanced.patches.reddit.customclients.baconreader.layout.avatars

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableField.Companion.toMutable
import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.firstClassDef
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.all.misc.resources.addResources
import app.revanced.patches.all.misc.resources.addResourcesPatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch
import app.revanced.patches.reddit.customclients.baconreader.misc.settings.PreferenceScreen
import app.revanced.patches.reddit.customclients.baconreader.misc.settings.settingsPatch
import app.revanced.patches.shared.misc.settings.preference.ListPreference
import app.revanced.patches.shared.misc.settings.preference.SwitchPreference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableField

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/baconreader/avatars/UserAvatarsPatch;"

@Suppress("unused")
val userAvatarsPatch = bytecodePatch(
    name = "Show user avatars",
    description = "Adds an option to show the author's avatar before the name in comments, as on Reddit's website.",
) {
    dependsOn(sharedExtensionPatch, settingsPatch, addResourcesPatch)

    compatibleWith(
        "com.onelouder.baconreader"("6.1.4"),
        "com.onelouder.baconreader.premium"("6.1.4"),
    )

    apply {
        addResources("baconreader", "layout.avatars.userAvatarsPatch")

        PreferenceScreen.COMMENTS.addPreferences(
            SwitchPreference("revanced_baconreader_user_avatars"),
            ListPreference("revanced_baconreader_user_avatar_size"),
            SwitchPreference("revanced_baconreader_user_avatar_background"),
        )

        // Reddit sends the author's id with every comment and message, but the app's Message class (the
        // superclass of Comment) has no field for it. The app's JSON parser (Jackson) fills public fields by name.
        firstClassDef(MESSAGE_CLASS_DESCRIPTOR).fields.add(
            ImmutableField(
                MESSAGE_CLASS_DESCRIPTOR,
                "author_fullname",
                "Ljava/lang/String;",
                AccessFlags.PUBLIC.value,
                null,
                null,
                null,
            ).toMutable(),
        )

        // The avatar goes before the author line of the comment.
        getCommentSubTextMethodMatch.method.apply {
            val returnIndex = getCommentSubTextMethodMatch[0]
            val subTextRegister = getInstruction<OneRegisterInstruction>(returnIndex).registerA
            addInstructions(
                returnIndex,
                """
                    invoke-static { v$subTextRegister }, $EXTENSION_CLASS_DESCRIPTOR->withAvatar(Landroid/text/Spannable;)Landroid/text/Spannable;
                    move-result-object v$subTextRegister
                """,
            )
            // The Comment parameter (p1; p0 is the Context) is reused for other values later in the method.
            addInstruction(
                0,
                "invoke-static { p1 }, $EXTENSION_CLASS_DESCRIPTOR->setComment(Ljava/lang/Object;)V",
            )
        }

        // The view to redraw when the avatar is loaded.
        commentHolderUpdateViewMethodMatch.method.apply {
            val setTextIndex = commentHolderUpdateViewMethodMatch[1]
            val viewRegister = getInstruction<FiveRegisterInstruction>(setTextIndex).registerC
            addInstruction(
                setTextIndex + 1,
                "invoke-static { v$viewRegister }, $EXTENSION_CLASS_DESCRIPTOR->bindAvatars(Landroid/widget/TextView;)V",
            )
        }

        // The avatar of the replied user on the reply screen.
        updateReplyTextMethodMatch.method.apply {
            val setTextIndex = updateReplyTextMethodMatch[1]
            val viewRegister = getInstruction<FiveRegisterInstruction>(setTextIndex).registerC
            addInstruction(
                setTextIndex + 1,
                "invoke-static { v$viewRegister }, $EXTENSION_CLASS_DESCRIPTOR->replyAvatar(Landroid/widget/TextView;)V",
            )
        }

        // The avatar of the author of inbox items.
        inboxHolderUpdateViewMethodMatch.method.apply {
            val setTextIndex = inboxHolderUpdateViewMethodMatch[1]
            val viewRegister = getInstruction<FiveRegisterInstruction>(setTextIndex).registerC
            addInstruction(
                setTextIndex + 1,
                "invoke-static { p0, v$viewRegister }, $EXTENSION_CLASS_DESCRIPTOR->inboxAvatar(Ljava/lang/Object;Landroid/widget/TextView;)V",
            )
        }
    }
}

