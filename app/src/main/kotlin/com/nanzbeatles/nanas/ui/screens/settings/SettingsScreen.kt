/**
 * Auramusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nanzbeatles.nanas.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.nanzbeatles.nanas.BuildConfig
import com.nanzbeatles.nanas.LocalPlayerAwareWindowInsets
import com.nanzbeatles.nanas.R
import com.nanzbeatles.nanas.constants.AccountChannelHandleKey
import com.nanzbeatles.nanas.constants.AccountEmailKey
import com.nanzbeatles.nanas.constants.AccountNameKey
import com.nanzbeatles.nanas.constants.DataSyncIdKey
import com.nanzbeatles.nanas.constants.InnerTubeCookieKey
import com.nanzbeatles.nanas.constants.NewReleaseNotificationsEnabledKey
import com.nanzbeatles.nanas.constants.UpdateArchitectureKey
import com.nanzbeatles.nanas.constants.UpdateVariantKey
import com.nanzbeatles.nanas.constants.UseLoginForBrowse
import com.nanzbeatles.nanas.constants.VisitorDataKey
import com.nanzbeatles.nanas.constants.YtmSyncKey
import com.nanzbeatles.nanas.notifications.NewReleaseNotificationScheduler
import com.nanzbeatles.nanas.ui.component.IconButton
import com.nanzbeatles.nanas.ui.component.InfoLabel
import com.nanzbeatles.nanas.ui.component.Material3SettingsGroup
import com.nanzbeatles.nanas.ui.component.Material3SettingsItem
import com.nanzbeatles.nanas.ui.component.TextFieldDialog
import com.nanzbeatles.nanas.ui.utils.backToMain
import com.nanzbeatles.nanas.utils.Updater
import com.nanzbeatles.nanas.utils.rememberPreference
import com.nanzbeatles.nanas.viewmodels.AccountSettingsViewModel
import com.nanzbeatles.nanas.viewmodels.HomeViewModel
import com.nanzbeatles.innertube.YouTube
import com.nanzbeatles.innertube.utils.parseCookieString

enum class SettingsTab(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
) {
    GENERAL(R.string.settings_section_general, R.drawable.tune),
    APPEARANCE(R.string.settings_section_ui, R.drawable.palette),
    PLAYER_CONTENT(R.string.settings_section_player_content, R.drawable.play),
    PRIVACY_STORAGE(R.string.settings_section_privacy_data, R.drawable.security),
    SYSTEM(R.string.settings_section_system, R.drawable.info),
    ALL(R.string.settings_tab_all, R.drawable.list),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val defaultVariant = if (BuildConfig.CAST_AVAILABLE) "gms" else "foss"
    val (updateVariant, _) = rememberPreference(UpdateVariantKey, defaultVariant)
    val (updateArchitecture, _) = rememberPreference(UpdateArchitectureKey, "automatic")
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val (accountNamePref, onAccountNameChange) = rememberPreference(AccountNameKey, "")
    val (accountEmail, onAccountEmailChange) = rememberPreference(AccountEmailKey, "")
    val (accountChannelHandle, onAccountChannelHandleChange) = rememberPreference(AccountChannelHandleKey, "")
    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val (visitorData, onVisitorDataChange) = rememberPreference(VisitorDataKey, "")
    val (dataSyncId, onDataSyncIdChange) = rememberPreference(DataSyncIdKey, "")

    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(UseLoginForBrowse, true)
    val (ytmSync, onYtmSyncChange) = rememberPreference(YtmSyncKey, true)
    val (newReleaseNotifications, onNewReleaseNotificationsChange) = rememberPreference(NewReleaseNotificationsEnabledKey, true)

    val homeViewModel: HomeViewModel = hiltViewModel()
    val accountSettingsViewModel: AccountSettingsViewModel = hiltViewModel()
    val accountName by homeViewModel.accountName.collectAsState()
    val accountImageUrl by homeViewModel.accountImageUrl.collectAsState()

    var showTokenEditor by rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(SettingsTab.GENERAL) }
    val contentScrollState = rememberScrollState()

    LaunchedEffect(selectedTab) {
        contentScrollState.scrollTo(0)
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.action_logout)) },
            text = { Text("Apakah Anda yakin ingin keluar dari akun YouTube Music?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        accountSettingsViewModel.logoutAndClearSyncedContent(context, onInnerTubeCookieChange)
                    }
                ) {
                    Text(stringResource(R.string.action_logout), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showTokenEditor) {
        val text = """
            ***INNERTUBE COOKIE*** =$innerTubeCookie
            ***VISITOR DATA*** =$visitorData
            ***DATASYNC ID*** =$dataSyncId
            ***ACCOUNT NAME*** =$accountNamePref
            ***ACCOUNT EMAIL*** =$accountEmail
            ***ACCOUNT CHANNEL HANDLE*** =$accountChannelHandle
        """.trimIndent()

        TextFieldDialog(
            initialTextFieldValue = TextFieldValue(text),
            onDone = { data ->
                var hasCookie = false
                data.split("\n").forEach { line ->
                    when {
                        line.startsWith("***INNERTUBE COOKIE*** =") -> {
                            onInnerTubeCookieChange(line.substringAfter("="))
                            hasCookie = true
                        }
                        line.startsWith("***VISITOR DATA*** =") -> onVisitorDataChange(line.substringAfter("="))
                        line.startsWith("***DATASYNC ID*** =") -> onDataSyncIdChange(line.substringAfter("="))
                        line.startsWith("***ACCOUNT NAME*** =") -> onAccountNameChange(line.substringAfter("="))
                        line.startsWith("***ACCOUNT EMAIL*** =") -> onAccountEmailChange(line.substringAfter("="))
                        line.startsWith("***ACCOUNT CHANNEL HANDLE*** =") -> onAccountChannelHandleChange(line.substringAfter("="))
                    }
                }
                if (!hasCookie && data.contains("SAPISID")) {
                    onInnerTubeCookieChange(data.trim())
                }
            },
            onDismiss = { showTokenEditor = false },
            singleLine = false,
            maxLines = 20,
            isInputValid = { input ->
                input.isNotBlank() && (input.contains("SAPISID") || input.contains("VISITOR DATA") || "SAPISID" in parseCookieString(input))
            },
            extraContent = {
                InfoLabel(text = stringResource(R.string.token_adv_login_description))
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null
                    )
                }
            }
        )

        // Horizontal navigation tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsTab.entries.forEach { tab ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    label = {
                        Text(
                            text = stringResource(tab.titleRes),
                            fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(tab.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    border = null
                )
            }
        }

        // Scrollable settings items
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(contentScrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTab) {
                SettingsTab.GENERAL -> {
                    GeneralSettingsContent(
                        navController = navController,
                        context = context,
                        isLoggedIn = isLoggedIn,
                        accountName = accountName,
                        accountImageUrl = accountImageUrl,
                        accountEmail = accountEmail,
                        accountChannelHandle = accountChannelHandle,
                        useLoginForBrowse = useLoginForBrowse,
                        onUseLoginForBrowseChange = onUseLoginForBrowseChange,
                        ytmSync = ytmSync,
                        onYtmSyncChange = onYtmSyncChange,
                        newReleaseNotifications = newReleaseNotifications,
                        onNewReleaseNotificationsChange = onNewReleaseNotificationsChange,
                        onShowTokenEditor = { showTokenEditor = true },
                        onShowLogoutDialog = { showLogoutDialog = true }
                    )
                }

                SettingsTab.APPEARANCE -> {
                    AppearanceSettingsContent(navController = navController)
                }

                SettingsTab.PLAYER_CONTENT -> {
                    PlayerContentSettingsContent(navController = navController)
                }

                SettingsTab.PRIVACY_STORAGE -> {
                    PrivacyStorageSettingsContent(navController = navController)
                }

                SettingsTab.SYSTEM -> {
                    SystemSettingsContent(
                        navController = navController,
                        context = context,
                        uriHandler = uriHandler,
                        isAndroid12OrLater = isAndroid12OrLater,
                        latestVersionName = latestVersionName,
                        updateVariant = updateVariant,
                        updateArchitecture = updateArchitecture
                    )
                }

                SettingsTab.ALL -> {
                    GeneralSettingsContent(
                        navController = navController,
                        context = context,
                        isLoggedIn = isLoggedIn,
                        accountName = accountName,
                        accountImageUrl = accountImageUrl,
                        accountEmail = accountEmail,
                        accountChannelHandle = accountChannelHandle,
                        useLoginForBrowse = useLoginForBrowse,
                        onUseLoginForBrowseChange = onUseLoginForBrowseChange,
                        ytmSync = ytmSync,
                        onYtmSyncChange = onYtmSyncChange,
                        newReleaseNotifications = newReleaseNotifications,
                        onNewReleaseNotificationsChange = onNewReleaseNotificationsChange,
                        onShowTokenEditor = { showTokenEditor = true },
                        onShowLogoutDialog = { showLogoutDialog = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    AppearanceSettingsContent(navController = navController)

                    Spacer(modifier = Modifier.height(16.dp))

                    PlayerContentSettingsContent(navController = navController)

                    Spacer(modifier = Modifier.height(16.dp))

                    PrivacyStorageSettingsContent(navController = navController)

                    Spacer(modifier = Modifier.height(16.dp))

                    SystemSettingsContent(
                        navController = navController,
                        context = context,
                        uriHandler = uriHandler,
                        isAndroid12OrLater = isAndroid12OrLater,
                        latestVersionName = latestVersionName,
                        updateVariant = updateVariant,
                        updateArchitecture = updateArchitecture
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GeneralSettingsContent(
    navController: NavController,
    context: android.content.Context,
    isLoggedIn: Boolean,
    accountName: String,
    accountImageUrl: String?,
    accountEmail: String,
    accountChannelHandle: String,
    useLoginForBrowse: Boolean,
    onUseLoginForBrowseChange: (Boolean) -> Unit,
    ytmSync: Boolean,
    onYtmSyncChange: (Boolean) -> Unit,
    newReleaseNotifications: Boolean,
    onNewReleaseNotificationsChange: (Boolean) -> Unit,
    onShowTokenEditor: () -> Unit,
    onShowLogoutDialog: () -> Unit,
) {
    // Account Card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                if (isLoggedIn) navController.navigate("account")
                else navController.navigate("login")
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoggedIn && accountImageUrl != null) {
                AsyncImage(
                    model = accountImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(if (isLoggedIn) R.drawable.account else R.drawable.login),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isLoggedIn) accountName.ifEmpty { stringResource(R.string.account) } else stringResource(R.string.login),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val subtitle = when {
                    isLoggedIn && accountEmail.isNotEmpty() -> accountEmail
                    isLoggedIn && accountChannelHandle.isNotEmpty() -> accountChannelHandle
                    isLoggedIn -> "Terhubung ke YouTube Music"
                    else -> "Masuk untuk sinkronisasi playlist & favorit"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isLoggedIn) {
                OutlinedButton(
                    onClick = onShowLogoutDialog,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.action_logout))
                }
            } else {
                Button(
                    onClick = { navController.navigate("login") },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.login))
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Account Options
    Material3SettingsGroup(
        title = stringResource(R.string.account),
        items = buildList {
            add(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.token),
                    title = { Text(stringResource(R.string.advanced_login)) },
                    description = {
                        Text(
                            if (isLoggedIn) stringResource(R.string.token_shown)
                            else stringResource(R.string.token_adv_login_description)
                        )
                    },
                    onClick = onShowTokenEditor
                )
            )
            if (isLoggedIn) {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.add_circle),
                        title = { Text(stringResource(R.string.more_content)) },
                        trailingContent = {
                            Switch(
                                checked = useLoginForBrowse,
                                onCheckedChange = {
                                    YouTube.useLoginForBrowse = it
                                    onUseLoginForBrowseChange(it)
                                }
                            )
                        },
                        onClick = {
                            val newVal = !useLoginForBrowse
                            YouTube.useLoginForBrowse = newVal
                            onUseLoginForBrowseChange(newVal)
                        }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.cached),
                        title = { Text(stringResource(R.string.yt_sync)) },
                        trailingContent = {
                            Switch(
                                checked = ytmSync,
                                onCheckedChange = onYtmSyncChange
                            )
                        },
                        onClick = { onYtmSyncChange(!ytmSync) }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.notification),
                        title = { Text(stringResource(R.string.new_release_notifications)) },
                        trailingContent = {
                            Switch(
                                checked = newReleaseNotifications,
                                onCheckedChange = { enabled ->
                                    onNewReleaseNotificationsChange(enabled)
                                    if (enabled) {
                                        NewReleaseNotificationScheduler.schedule(context)
                                    } else {
                                        NewReleaseNotificationScheduler.cancel(context)
                                    }
                                }
                            )
                        },
                        onClick = {
                            val enabled = !newReleaseNotifications
                            onNewReleaseNotificationsChange(enabled)
                            if (enabled) {
                                NewReleaseNotificationScheduler.schedule(context)
                            } else {
                                NewReleaseNotificationScheduler.cancel(context)
                            }
                        }
                    )
                )
            }
        }
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Integrations
    Material3SettingsGroup(
        title = stringResource(R.string.integrations),
        items = listOf(
            Material3SettingsItem(
                icon = painterResource(R.drawable.discord),
                title = { Text(stringResource(R.string.discord_integration)) },
                description = { Text("Tampilkan aktivitas lagu di profil Discord") },
                onClick = { navController.navigate("settings/integrations/discord") }
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.music_note),
                title = { Text(stringResource(R.string.lastfm_integration)) },
                description = { Text("Scrobble otomatis lagu yang Anda dengarkan") },
                onClick = { navController.navigate("settings/integrations/lastfm") }
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.group),
                title = { Text(stringResource(R.string.listen_together)) },
                description = { Text(stringResource(R.string.listen_together_description)) },
                onClick = { navController.navigate("settings/integrations/listen_together") }
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.integration),
                title = { Text(stringResource(R.string.integrations)) },
                description = { Text("Kelola semua koneksi dan integrasi pihak ketiga") },
                onClick = { navController.navigate("settings/integrations") }
            )
        )
    )
}

@Composable
private fun AppearanceSettingsContent(
    navController: NavController
) {
    Material3SettingsGroup(
        title = stringResource(R.string.settings_section_ui),
        items = listOf(
            Material3SettingsItem(
                icon = painterResource(R.drawable.palette),
                title = { Text(stringResource(R.string.appearance)) },
                description = { Text("Tema, tata letak, gaya visual, dan pemutar mini") },
                onClick = { navController.navigate("settings/appearance") }
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.palette),
                title = { Text(stringResource(R.string.theme)) },
                description = { Text(stringResource(R.string.theme_desc)) },
                onClick = { navController.navigate("settings/appearance/theme") }
            )
        )
    )
}

@Composable
private fun PlayerContentSettingsContent(
    navController: NavController
) {
    Material3SettingsGroup(
        title = stringResource(R.string.settings_section_player_content),
        items = listOf(
            Material3SettingsItem(
                icon = painterResource(R.drawable.play),
                title = { Text(stringResource(R.string.player_and_audio)) },
                description = { Text("Kualitas audio, normalisasi volume, dan kontrol pemutar") },
                onClick = { navController.navigate("settings/player") }
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.language),
                title = { Text(stringResource(R.string.content)) },
                description = { Text("Bahasa konten, lokasi, dan prioritas penyedia lirik") },
                onClick = { navController.navigate("settings/content") }
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.translate),
                title = { Text(stringResource(R.string.ai_lyrics_translation)) },
                description = { Text("Terjemahan lirik otomatis multibahasa menggunakan AI") },
                onClick = { navController.navigate("settings/ai") }
            )
        )
    )
}

@Composable
private fun PrivacyStorageSettingsContent(
    navController: NavController
) {
    Material3SettingsGroup(
        title = stringResource(R.string.settings_section_privacy_data),
        items = listOf(
            Material3SettingsItem(
                icon = painterResource(R.drawable.security),
                title = { Text(stringResource(R.string.privacy)) },
                description = { Text("Riwayat pemutaran, pencarian, dan analitik") },
                onClick = { navController.navigate("settings/privacy") }
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.storage),
                title = { Text(stringResource(R.string.storage)) },
                description = { Text("Penyimpanan cache audio, gambar, dan unduhan") },
                onClick = { navController.navigate("settings/storage") }
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.restore),
                title = { Text(stringResource(R.string.backup_restore)) },
                description = { Text("Cadangkan dan pulihkan data, pengaturan, atau daftar putar") },
                onClick = { navController.navigate("settings/backup_restore") }
            )
        )
    )
}

@Composable
private fun SystemSettingsContent(
    navController: NavController,
    context: android.content.Context,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    isAndroid12OrLater: Boolean,
    latestVersionName: String,
    updateVariant: String,
    updateArchitecture: String
) {
    Material3SettingsGroup(
        title = stringResource(R.string.settings_section_system),
        items = buildList {
            if (isAndroid12OrLater) {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.link),
                        title = { Text(stringResource(R.string.default_links)) },
                        description = { Text("Buka tautan YouTube Music secara otomatis di aplikasi") },
                        onClick = {
                            try {
                                val intent = Intent(
                                    Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                    "package:${context.packageName}".toUri()
                                )
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    R.string.open_app_settings_error,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                )
            }
            add(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.update),
                    title = { Text(stringResource(R.string.updater)) },
                    description = { Text("Periksa pembaruan versi terbaru aplikasi") },
                    onClick = { navController.navigate("settings/updater") }
                )
            )
            if (latestVersionName != BuildConfig.VERSION_NAME) {
                val releaseInfo = Updater.getCachedLatestRelease()
                val downloadUrl = releaseInfo?.let {
                    Updater.getDownloadUrlForCurrentVariant(it, updateVariant, updateArchitecture)
                }

                if (downloadUrl != null) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.update),
                            title = {
                                Text(
                                    text = stringResource(R.string.new_version_available),
                                )
                            },
                            description = {
                                Text(
                                    text = latestVersionName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            showBadge = true,
                            onClick = { uriHandler.openUri(downloadUrl) }
                        )
                    )
                }
            }
            add(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.whatsapp),
                    title = { Text("Join Channels WhatsApp") },
                    description = { Text("Gabung saluran WhatsApp resmi NanzBeatles") },
                    onClick = { uriHandler.openUri("https://whatsapp.com/channel/0029VbCsS2r2phHIV3O0nO1a") }
                )
            )
        }
    )
}
