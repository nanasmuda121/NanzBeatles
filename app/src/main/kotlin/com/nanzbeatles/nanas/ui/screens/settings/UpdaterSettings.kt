/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nanzbeatles.nanas.BuildConfig
import com.nanzbeatles.nanas.LocalPlayerAwareWindowInsets
import com.nanzbeatles.nanas.R
import com.nanzbeatles.nanas.constants.CheckForUpdatesKey
import com.nanzbeatles.nanas.constants.UpdateArchitectureKey
import com.nanzbeatles.nanas.constants.UpdateNotificationsEnabledKey
import com.nanzbeatles.nanas.constants.UpdateVariantKey
import com.nanzbeatles.nanas.ui.component.IconButton
import com.nanzbeatles.nanas.ui.component.Material3SettingsGroup
import com.nanzbeatles.nanas.ui.component.Material3SettingsItem
import com.nanzbeatles.nanas.ui.utils.backToMain
import com.nanzbeatles.nanas.utils.Updater
import com.nanzbeatles.nanas.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdaterScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (checkForUpdates, onCheckForUpdatesChange) = rememberPreference(CheckForUpdatesKey, true)
    val (updateNotifications, onUpdateNotificationsChange) = rememberPreference(UpdateNotificationsEnabledKey, true)
    val defaultVariant = if (BuildConfig.CAST_AVAILABLE) "gms" else "foss"
    val (updateVariant, onUpdateVariantChange) = rememberPreference(UpdateVariantKey, defaultVariant)
    val (updateArchitecture, onUpdateArchitectureChange) = rememberPreference(UpdateArchitectureKey, "automatic")
    
    var isChecking by remember { mutableStateOf(false) }
    var updateAvailable by remember { mutableStateOf(false) }
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var checkError by remember { mutableStateOf<String?>(null) }
    
    val coroutineScope = rememberCoroutineScope()

    fun performManualCheck() {
        coroutineScope.launch {
            isChecking = true
            checkError = null
            withContext(Dispatchers.IO) {
                Updater.checkForUpdate(forceRefresh = true).onSuccess { (releaseInfo, hasUpdate) ->
                    if (releaseInfo != null) {
                        latestVersion = releaseInfo.versionName
                        updateAvailable = hasUpdate
                    }
                }.onFailure {
                    checkError = "Gagal memeriksa pembaruan: ${it.message}"
                }
            }
            isChecking = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        Spacer(Modifier.height(4.dp))

        // Current Version Info
        Material3SettingsGroup(
            title = "Versi Saat Ini",
            items = listOf(
                Material3SettingsItem(
                    title = {
                        Text("Versi: ${BuildConfig.VERSION_NAME}")
                    },
                    description = {
                        val arch = BuildConfig.ARCHITECTURE
                        val variant = when {
                            BuildConfig.CAST_AVAILABLE -> "GMS"
                            arch != "universal" -> "Standalone"
                            else -> "FOSS"
                        }
                        Text("$arch - $variant")
                    }
                )
            )
        )
        
        Spacer(Modifier.height(16.dp))

        // Auto Update Settings
        Material3SettingsGroup(
            title = "Pengaturan Pembaruan",
            items = buildList {
                add(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.check_for_updates)) },
                        icon = painterResource(R.drawable.update),
                        trailingContent = {
                            Switch(
                                checked = checkForUpdates,
                                onCheckedChange = onCheckForUpdatesChange
                            )
                        },
                        onClick = { onCheckForUpdatesChange(!checkForUpdates) }
                    )
                )

                if (checkForUpdates) {
                    add(
                        Material3SettingsItem(
                            title = { Text(stringResource(R.string.update_notifications)) },
                            icon = painterResource(R.drawable.notification),
                            trailingContent = {
                                Switch(
                                    checked = updateNotifications,
                                    onCheckedChange = onUpdateNotificationsChange
                                )
                            },
                            onClick = { onUpdateNotificationsChange(!updateNotifications) }
                        )
                    )
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        // APK Variant Selection
        Material3SettingsGroup(
            title = "Varian APK",
            items = listOf(
                Material3SettingsItem(
                    title = { Text("NanzBeatles") },
                    description = { Text("Versi standar tanpa Google Cast") },
                    trailingContent = {
                        androidx.compose.material3.RadioButton(
                            selected = updateVariant == "foss",
                            onClick = { onUpdateVariantChange("foss") }
                        )
                    },
                    onClick = { onUpdateVariantChange("foss") }
                ),
                Material3SettingsItem(
                    title = { Text("NanzBeatles dengan Google Cast") },
                    description = { Text("Termasuk dukungan Google Cast") },
                    trailingContent = {
                        androidx.compose.material3.RadioButton(
                            selected = updateVariant == "gms",
                            onClick = { onUpdateVariantChange("gms") }
                        )
                    },
                    onClick = { onUpdateVariantChange("gms") }
                )
            )
        )

        Spacer(Modifier.height(16.dp))

        Material3SettingsGroup(
            title = "Arsitektur APK",
            items = listOf(
                "automatic" to "Otomatis / perangkat ini (${BuildConfig.ARCHITECTURE})",
                "universal" to "Universal",
                "arm64" to "ARM64 (manual)",
                "armeabi" to "ARMv7 (armeabi) (manual)",
                "x86" to "x86 (manual)",
                "x86_64" to "x86_64 (manual)",
            ).map { (value, label) ->
                Material3SettingsItem(
                    title = { Text(label) },
                    description = if (value == "automatic" || value == "universal") null else {
                        { Text("Beralih ke Universal jika APK ini tidak tersedia") }
                    },
                    trailingContent = {
                        androidx.compose.material3.RadioButton(
                            selected = updateArchitecture == value,
                            onClick = { onUpdateArchitectureChange(value) },
                        )
                    },
                    onClick = { onUpdateArchitectureChange(value) },
                )
            },
        )

        Spacer(Modifier.height(16.dp))

        // Manual Check
        Material3SettingsGroup(
            title = "Periksa Pembaruan",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.refresh),
                    title = { 
                        if (isChecking) {
                            Text("Memeriksa pembaruan...")
                        } else if (latestVersion != null) {
                            Text("Terbaru: $latestVersion")
                        } else {
                            Text("Periksa Pembaruan")
                        }
                    },
                    trailingContent = {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 16.dp),
                                strokeWidth = 2.dp
                            )
                        } else if (updateAvailable) {
                            Icon(
                                painter = painterResource(R.drawable.download),
                                contentDescription = "Pembaruan tersedia",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    onClick = { if (!isChecking) performManualCheck() }
                )
            )
        )

        checkError?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.updater)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )
}
