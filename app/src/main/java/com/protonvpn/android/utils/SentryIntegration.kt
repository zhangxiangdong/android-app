/*
 * Copyright (c) 2021. Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.android.utils

import android.app.Application
import com.protonvpn.android.BuildConfig
import io.sentry.Sentry
import io.sentry.android.AndroidSentryClientFactory
import io.sentry.event.UserBuilder
import java.util.UUID

object SentryIntegration {

    private const val SENTRY_INSTALLATION_ID_KEY = "sentry_installation_id"

    @JvmStatic
    fun getInstallationId(): String =
        Storage.getString(SENTRY_INSTALLATION_ID_KEY, null)
            ?: UUID.randomUUID().toString().also {
                Storage.saveString(SENTRY_INSTALLATION_ID_KEY, it)
            }

    @JvmStatic
    fun initSentry(app: Application) {
        val sentryDsn = if (!BuildConfig.DEBUG) BuildConfig.Sentry_DSN else null

        val client = Sentry.init(sentryDsn, AndroidSentryClientFactory(app))
        client.context.user = UserBuilder().setId(getInstallationId()).build()

        val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(ProtonExceptionHandler(currentHandler))
    }
}