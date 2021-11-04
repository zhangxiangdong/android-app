/*
 * Copyright (c) 2021 Proton Technologies AG
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

package com.protonvpn.android.auth.usecase

import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.VpnConnectionManager
import kotlinx.coroutines.flow.MutableSharedFlow
import me.proton.core.account.domain.entity.Account
import me.proton.core.accountmanager.domain.AccountManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnSessionClosed @Inject constructor(
    val accountManager: AccountManager,
    val vpnConnectionManager: VpnConnectionManager,
    val certificateRepository: CertificateRepository,
    val userData: UserData,
    val serverManager: ServerManager
) {
    val logoutFlow = MutableSharedFlow<Account>()

    suspend operator fun invoke(account: Account) {
        accountManager.removeAccount(account.userId)
        vpnConnectionManager.disconnectSync()
        account.sessionId?.let { certificateRepository.clear(it) }
        userData.onLogout()
        serverManager.clearCache()
        logoutFlow.emit(account)
    }
}