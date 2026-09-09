/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodSubtype
import org.fcitx.fcitx5.android.BuildConfig
import org.fcitx.fcitx5.android.input.FcitxInputMethodService

object InputMethodUtil {

    @JvmField
    val serviceName: String = FcitxInputMethodService::class.java.name

    @JvmField
    val componentName: String =
        ComponentName(appContext, FcitxInputMethodService::class.java).flattenToShortString()

    fun isEnabled(): Boolean {
        return appContext.inputMethodManager.enabledInputMethodList.any {
            it.packageName == BuildConfig.APPLICATION_ID && it.serviceName == serviceName
        }
    }

    fun isSelected(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            appContext.inputMethodManager.currentInputMethodInfo?.let {
                it.packageName == BuildConfig.APPLICATION_ID && it.serviceName == serviceName
            } ?: false
        } else {
            getSecureSettings<String>(Settings.Secure.DEFAULT_INPUT_METHOD) == componentName
        }
    }

    fun startSettingsActivity(context: Context) =
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

    fun showPicker() = appContext.inputMethodManager.showInputMethodPicker()

    /**
     * Every enabled input method except ourselves, paired with its `"voice"`
     * subtype when it declares one.
     *
     * Not every voice input declares a `"voice"` subtype -- Samsung's does not
     * on some devices -- so the list is deliberately not filtered by that.
     */
    fun listSwitchTargets(): List<Pair<InputMethodInfo, InputMethodSubtype?>> {
        return appContext.inputMethodManager.enabledInputMethodList
            .filterNot { it.packageName == BuildConfig.APPLICATION_ID }
            .map { it to it.firstVoiceSubtype() }
    }

    /**
     * The input method to switch to, preferring the one with [id]. An empty
     * [id] means "pick one", which is the first target declaring a voice
     * subtype. The subtype is null when the target declares none, in which
     * case it is switched to without selecting a subtype.
     */
    fun findSwitchTarget(id: String): Pair<String, InputMethodSubtype?>? {
        val targets = listSwitchTargets()
        targets.find { (info, _) -> info.id == id }?.let { (info, subtype) ->
            return info.id to subtype
        }
        val withVoice = targets.find { (_, subtype) -> subtype != null } ?: return null
        return withVoice.first.id to withVoice.second
    }

    fun switchInputMethod(
        service: FcitxInputMethodService,
        id: String,
        subtype: InputMethodSubtype?
    ) {
        if (subtype == null) {
            service.switchInputMethod(id)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            service.switchInputMethod(id, subtype)
        } else {
            @Suppress("DEPRECATION")
            appContext.inputMethodManager
                .setInputMethodAndSubtype(service.window.window!!.attributes.token, id, subtype)
        }
    }
}