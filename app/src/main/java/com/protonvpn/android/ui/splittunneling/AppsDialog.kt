/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.android.ui.splittunneling

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.TextUtils
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import butterknife.OnClick
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseDialog
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.utils.sortedByLocaleAware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val MAX_SAFE_LABEL_CHARS = 1000
private const val MAX_SAFE_LABEL_DP = 500f

@ContentLayout(R.layout.dialog_split_tunnel)
class AppsDialog : BaseDialog() {
    private lateinit var adapter: AppsAdapter

    @BindView(R.id.textTitle)
    lateinit var textTitle: TextView

    @BindView(R.id.textDescription)
    lateinit var textDescription: TextView

    @BindView(R.id.list)
    lateinit var list: RecyclerView

    @BindView(R.id.progressBar)
    lateinit var progressBar: ProgressBar

    @Inject
    lateinit var userData: UserData
    @Inject
    lateinit var activityManager: ActivityManager

    override fun onViewCreated() {
        list.layoutManager = LinearLayoutManager(activity)
        adapter = AppsAdapter(userData)
        list.adapter = adapter
        textTitle.setText(R.string.excludeAppsTitle)
        textDescription.setText(R.string.excludeAppsDescription)
        progressBar.visibility = View.VISIBLE

        val selection = userData.splitTunnelApps.toSet()
        viewLifecycleOwner.lifecycleScope.launch {
            val allApps = getInstalledInternetApps(requireContext().applicationContext)
            val sortedApps = withContext(Dispatchers.Default) {
                allApps.forEach { app ->
                    if (selection.contains(app.packageName)) {
                        app.isSelected = true
                    }
                }
                allApps.sortedByLocaleAware { it.toString() }
            }
            removeUninstalledApps(userData, allApps)
            adapter.setData(sortedApps)
            adapter.notifyDataSetChanged()
            progressBar.visibility = View.GONE
        }
    }

    @OnClick(R.id.textDone)
    fun textDone() {
        dismiss()
    }

    private fun removeUninstalledApps(userData: UserData, allApps: List<SelectedApplicationEntry>) {
        val userDataAppPackages = userData.splitTunnelApps
        val allAppPackages = HashSet<String>(allApps.size)
        allApps.mapTo(allAppPackages) { it.packageName }
        userDataAppPackages
            .filterNot { allAppPackages.contains(it) }
            .forEach { userData.removeAppFromSplitTunnel(it) }
    }

    // Pass app context to avoid problems when the dialog is closed and the IO thread is still
    // accessing the PackageManager.
    private suspend fun getInstalledInternetApps(
        appContext: Context
    ): List<SelectedApplicationEntry> = withContext(Dispatchers.IO) {
        val packageManager = appContext.packageManager
        val ourPackageName = appContext.packageName
        val apps = packageManager.getInstalledApplications(
            0
        ).filter { appInfo ->
            (packageManager.checkPermission(Manifest.permission.INTERNET, appInfo.packageName)
                    == PackageManager.PERMISSION_GRANTED)
        }.map { appInfo ->
            ensureActive()
            getAppMetadata(packageManager, appInfo, ourPackageName)
        }
        apps
    }

    private fun getAppMetadata(
        packageManager: PackageManager,
        appInfo: ApplicationInfo,
        ourPackageName: String
    ): SelectedApplicationEntry {
        val appResources = packageManager.getResourcesForApplication(appInfo)
        val label = getLabel(appResources, appInfo.labelRes) ?: appInfo.packageName
        val icon = getIcon(appResources, appInfo.icon) ?: packageManager.defaultActivityIcon
        if (appInfo.packageName != ourPackageName) {
            appResources.assets.close()
        }
        return SelectedApplicationEntry(appInfo.packageName, label.toString(), icon)
    }

    private fun getLabel(appResources: Resources, labelResource: Int): String? =
        try {
            val label = appResources.getString(labelResource)
            if (Build.VERSION.SDK_INT >= 29) {
                val flags = TextUtils.SAFE_STRING_FLAG_TRIM or TextUtils.SAFE_STRING_FLAG_FIRST_LINE
                TextUtils
                    .makeSafeForPresentation(label, MAX_SAFE_LABEL_CHARS, MAX_SAFE_LABEL_DP, flags)
                    .toString()
            } else {
                label
            }
        } catch (e: Resources.NotFoundException) {
            // For some app packages resources fail to load, as if the ID is incorrect.
            null
        }

    private fun getIcon(appResources: Resources, iconResource: Int): Drawable? =
        try {
            ResourcesCompat.getDrawable(appResources, iconResource, null)
        } catch (e: Resources.NotFoundException) {
            null
        }
}
