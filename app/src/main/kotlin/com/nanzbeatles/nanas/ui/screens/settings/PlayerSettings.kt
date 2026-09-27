/**
 * Auramusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nanzbeatles.nanas.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.nanzbeatles.nanas.BuildConfig
import com.nanzbeatles.nanas.LocalPlayerAwareWindowInsets
import com.nanzbeatles.nanas.R
import com.nanzbeatles.nanas.constants.AudioNormalizationKey
import com.nanzbeatles.nanas.constants.AudioOffload
import com.nanzbeatles.nanas.constants.AudioQuality
import com.nanzbeatles.nanas.constants.AudioQualityKey
import com.nanzbeatles.nanas.constants.AutoDownloadOnLikeKey
import com.nanzbeatles.nanas.constants.CrossfadeDurationKey
import com.nanzbeatles.nanas.constants.CrossfadeEnabledKey
import com.nanzbeatles.nanas.constants.AutomixEnabledKey
import com.nanzbeatles.nanas.constants.AutomixBlendPercentKey
import com.nanzbeatles.nanas.constants.CrossfadeGaplessKey
import com.nanzbeatles.nanas.constants.AutoLoadMoreKey
import com.nanzbeatles.nanas.constants.AutoSkipNextOnErrorKey
import com.nanzbeatles.nanas.constants.DisableLoadMoreWhenRepeatAllKey
import com.nanzbeatles.nanas.constants.EnableGoogleCastKey
import com.nanzbeatles.nanas.constants.EnableVoiceCommandsKey
import com.nanzbeatles.nanas.constants.EnableVoiceWakeWordKey
import com.nanzbeatles.nanas.constants.HistoryDuration
import com.nanzbeatles.nanas.constants.KeepScreenOn
import com.nanzbeatles.nanas.constants.LateNightModeKey
import com.nanzbeatles.nanas.constants.PauseOnMute
import com.nanzbeatles.nanas.constants.PersistentQueueKey
import com.nanzbeatles.nanas.constants.PersistentShuffleAcrossQueuesKey
import com.nanzbeatles.nanas.constants.RememberShuffleAndRepeatKey
import com.nanzbeatles.nanas.constants.SeekExtraSeconds
import com.nanzbeatles.nanas.constants.SponsorBlockEnabledKey
import com.nanzbeatles.nanas.constants.SponsorBlockSkipSponsorKey
import com.nanzbeatles.nanas.constants.SponsorBlockSkipSelfPromoKey
import com.nanzbeatles.nanas.constants.SponsorBlockSkipInteractionKey
import com.nanzbeatles.nanas.constants.SponsorBlockSkipIntroKey
import com.nanzbeatles.nanas.constants.SponsorBlockSkipOutroKey
import com.nanzbeatles.nanas.constants.SponsorBlockSkipPreviewKey
import com.nanzbeatles.nanas.constants.SponsorBlockSkipMusicOffTopicKey
import com.nanzbeatles.nanas.constants.SponsorBlockSkipFillerKey
import com.nanzbeatles.nanas.constants.ShufflePlaylistFirstKey
import com.nanzbeatles.nanas.constants.AuraCanvasEnabledKey
import com.nanzbeatles.nanas.constants.SimilarContent
import com.nanzbeatles.nanas.constants.SkipSilenceInstantKey
import com.nanzbeatles.nanas.constants.SkipSilenceKey
import com.nanzbeatles.nanas.constants.StopMusicOnTaskClearKey
import com.nanzbeatles.nanas.constants.SubtitlesEnabledKey
import com.nanzbeatles.nanas.constants.SubtitleFontSizeKey
import com.nanzbeatles.nanas.constants.SubtitleLanguageKey
import com.nanzbeatles.nanas.constants.VideoModeEnabledKey
import com.nanzbeatles.nanas.constants.VoiceWakeWordKey
import com.nanzbeatles.nanas.ui.component.DefaultDialog
import com.nanzbeatles.nanas.ui.component.EnumDialog
import com.nanzbeatles.nanas.ui.component.IconButton
import com.nanzbeatles.nanas.ui.component.ListDialog
import com.nanzbeatles.nanas.ui.component.Material3SettingsGroup
import com.nanzbeatles.nanas.ui.component.Material3SettingsItem
import com.nanzbeatles.nanas.ui.utils.backToMain
import com.nanzbeatles.nanas.utils.rememberEnumPreference
import com.nanzbeatles.nanas.utils.rememberPreference
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(
        AudioQualityKey,
        defaultValue = AudioQuality.AUTO
    )
    val (crossfadeEnabled, onCrossfadeEnabledChange) = rememberPreference(
        CrossfadeEnabledKey,
        defaultValue = false
    )
    val (crossfadeDuration, onCrossfadeDurationChange) = rememberPreference(
        CrossfadeDurationKey,
        defaultValue = 5f
    )
    val (crossfadeGapless, onCrossfadeGaplessChange) = rememberPreference(
        CrossfadeGaplessKey,
        defaultValue = true
    )
    val (automixEnabled, onAutomixEnabledChange) = rememberPreference(
        AutomixEnabledKey,
        defaultValue = false
    )
    val (automixBlendPercent, onAutomixBlendPercentChange) = rememberPreference(
        AutomixBlendPercentKey,
        defaultValue = 90f
    )
    val (persistentQueue, onPersistentQueueChange) = rememberPreference(
        PersistentQueueKey,
        defaultValue = true
    )
    val (skipSilence, onSkipSilenceChange) = rememberPreference(
        SkipSilenceKey,
        defaultValue = false
    )
    val (skipSilenceInstant, onSkipSilenceInstantChange) = rememberPreference(
        SkipSilenceInstantKey,
        defaultValue = false
    )
    val (audioNormalization, onAudioNormalizationChange) = rememberPreference(
        AudioNormalizationKey,
        defaultValue = true
    )
    val (lateNightMode, onLateNightModeChange) = rememberPreference(
        LateNightModeKey,
        defaultValue = false
    )

    val (audioOffload, onAudioOffloadChange) = rememberPreference(
        key = AudioOffload,
        defaultValue = false
    )

    val (enableGoogleCast, onEnableGoogleCastChange) = rememberPreference(
        key = EnableGoogleCastKey,
        defaultValue = true
    )

    val (seekExtraSeconds, onSeekExtraSeconds) = rememberPreference(
        SeekExtraSeconds,
        defaultValue = false
    )

    val (sponsorBlockEnabled, onSponsorBlockEnabledChange) = rememberPreference(
        SponsorBlockEnabledKey, false,
    )
    val (sbSkipSponsor, onSbSkipSponsorChange) = rememberPreference(SponsorBlockSkipSponsorKey, true)
    val (sbSkipSelfPromo, onSbSkipSelfPromoChange) = rememberPreference(SponsorBlockSkipSelfPromoKey, true)
    val (sbSkipInteraction, onSbSkipInteractionChange) = rememberPreference(SponsorBlockSkipInteractionKey, true)
    val (sbSkipIntro, onSbSkipIntroChange) = rememberPreference(SponsorBlockSkipIntroKey, true)
    val (sbSkipOutro, onSbSkipOutroChange) = rememberPreference(SponsorBlockSkipOutroKey, true)
    val (sbSkipPreview, onSbSkipPreviewChange) = rememberPreference(SponsorBlockSkipPreviewKey, true)
    val (sbSkipMusicOffTopic, onSbSkipMusicOffTopicChange) = rememberPreference(SponsorBlockSkipMusicOffTopicKey, true)
    val (sbSkipFiller, onSbSkipFillerChange) = rememberPreference(SponsorBlockSkipFillerKey, true)

    val (autoLoadMore, onAutoLoadMoreChange) = rememberPreference(
        AutoLoadMoreKey,
        defaultValue = true
    )
    val (disableLoadMoreWhenRepeatAll, onDisableLoadMoreWhenRepeatAllChange) = rememberPreference(
        DisableLoadMoreWhenRepeatAllKey,
        defaultValue = false
    )
    val (autoDownloadOnLike, onAutoDownloadOnLikeChange) = rememberPreference(
        AutoDownloadOnLikeKey,
        defaultValue = false
    )
    val (similarContentEnabled, similarContentEnabledChange) = rememberPreference(
        key = SimilarContent,
        defaultValue = true
    )
    val (autoSkipNextOnError, onAutoSkipNextOnErrorChange) = rememberPreference(
        AutoSkipNextOnErrorKey,
        defaultValue = true
    )
    val (persistentShuffleAcrossQueues, onPersistentShuffleAcrossQueuesChange) = rememberPreference(
        PersistentShuffleAcrossQueuesKey,
        defaultValue = false
    )
    val (rememberShuffleAndRepeat, onRememberShuffleAndRepeatChange) = rememberPreference(
        RememberShuffleAndRepeatKey,
        defaultValue = true
    )
    val (shufflePlaylistFirst, onShufflePlaylistFirstChange) = rememberPreference(
        ShufflePlaylistFirstKey,
        defaultValue = false
    )
    val (stopMusicOnTaskClear, onStopMusicOnTaskClearChange) = rememberPreference(
        StopMusicOnTaskClearKey,
        defaultValue = false
    )
    val (videoModeEnabled, onVideoModeEnabledChange) = rememberPreference(
        VideoModeEnabledKey,
        defaultValue = true
    )
    val (auraCanvasEnabled, onAuraCanvasEnabledChange) = rememberPreference(
        AuraCanvasEnabledKey,
        defaultValue = true
    )
    val (subtitlesEnabled, onSubtitlesEnabledChange) = rememberPreference(
        SubtitlesEnabledKey,
        defaultValue = true
    )
    val (subtitleLanguage, onSubtitleLanguageChange) = rememberPreference(
        SubtitleLanguageKey,
        defaultValue = "auto"
    )
    val (subtitleFontSize, onSubtitleFontSizeChange) = rememberPreference(
        SubtitleFontSizeKey,
        defaultValue = 16f
    )
    val (pauseOnMute, onPauseOnMuteChange) = rememberPreference(
        PauseOnMute,
        defaultValue = false
    )
    val (keepScreenOn, onKeepScreenOnChange) = rememberPreference(
        KeepScreenOn,
        defaultValue = false
    )
    val (historyDuration, onHistoryDurationChange) = rememberPreference(
        HistoryDuration,
        defaultValue = 30f
    )
    val (enableVoiceCommands, onEnableVoiceCommandsChange) = rememberPreference(EnableVoiceCommandsKey, defaultValue = true)
    val (enableVoiceWakeWord, onEnableVoiceWakeWordChange) = rememberPreference(EnableVoiceWakeWordKey, defaultValue = false)
    val (voiceWakeWord, onVoiceWakeWordChange) = rememberPreference(VoiceWakeWordKey, defaultValue = "Beatles")

    var showAudioQualityDialog by remember {
        mutableStateOf(false)
    }

    if (showAudioQualityDialog) {
        EnumDialog(
            onDismiss = { showAudioQualityDialog = false },
            onSelect = {
                onAudioQualityChange(it)
                showAudioQualityDialog = false
            },
            title = stringResource(R.string.audio_quality),
            current = audioQuality,
            values = AudioQuality.values().toList(),
            valueText = {
                when (it) {
                    AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
                    AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
                    AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
                }
            }
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        var showCrossfadeBetaDialog by remember { mutableStateOf(false) }

        if (showCrossfadeBetaDialog) {
            DefaultDialog(
                onDismiss = { showCrossfadeBetaDialog = false },
                title = { Text(stringResource(R.string.crossfade_beta_title)) },
                buttons = {
                    TextButton(onClick = { showCrossfadeBetaDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(onClick = {
                        showCrossfadeBetaDialog = false
                        onCrossfadeEnabledChange(true)
                    }) {
                        Text(stringResource(R.string.enable))
                    }
                }
            ) {
                Text(stringResource(R.string.crossfade_beta_message))
            }
        }

        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        Material3SettingsGroup(
            title = stringResource(R.string.alarm_title),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.alarm),
                    title = { Text(stringResource(R.string.alarm_title)) },
                    description = { Text("Bangun dengan lagu unduhan, cache, atau daftar putar Anda") },
                    onClick = { navController.navigate("settings/alarm") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.player),
            items = buildList {
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.graphic_eq),
                    title = { Text(stringResource(R.string.audio_quality)) },
                    description = {
                        Text(
                            when (audioQuality) {
                                AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
                                AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
                                AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
                            }
                        )
                    },
                    onClick = { showAudioQualityDialog = true }
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.linear_scale),
                    title = { Text(stringResource(R.string.crossfade)) },
                    description = {
                        Text(
                            if (crossfadeEnabled) {
                                "${pluralStringResource(R.plurals.seconds, crossfadeDuration.toInt(), crossfadeDuration.toInt())} • ${stringResource(R.string.crossfade_desc)}"
                            } else {
                                stringResource(R.string.crossfade_desc)
                            }
                        )
                    },
                    showBadge = true,
                    trailingContent = {
                        Switch(
                            checked = crossfadeEnabled,
                            onCheckedChange = {
                                if (!crossfadeEnabled) {
                                    showCrossfadeBetaDialog = true
                                } else {
                                    onCrossfadeEnabledChange(false)
                                }
                            },
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (crossfadeEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = {
                        if (!crossfadeEnabled) {
                            showCrossfadeBetaDialog = true
                        } else {
                            onCrossfadeEnabledChange(false)
                        }
                    }
                ))
                if (crossfadeEnabled) {
                    add(Material3SettingsItem(
                        icon = painterResource(R.drawable.timer),
                        title = { Text(stringResource(R.string.crossfade_duration)) },
                        description = {
                            Column {
                                Text(pluralStringResource(R.plurals.seconds, crossfadeDuration.toInt(), crossfadeDuration.toInt()))
                                Slider(
                                    value = crossfadeDuration,
                                    onValueChange = onCrossfadeDurationChange,
                                    valueRange = 1f..12f,
                                    steps = 11
                                )
                            }
                        }
                    ))
                    add(Material3SettingsItem(
                        icon = painterResource(R.drawable.album),
                        title = { Text(stringResource(R.string.crossfade_gapless)) },
                        description = { Text(stringResource(R.string.crossfade_gapless_desc)) },
                        trailingContent = {
                            Switch(
                                checked = crossfadeGapless,
                                onCheckedChange = onCrossfadeGaplessChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (crossfadeGapless) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onCrossfadeGaplessChange(!crossfadeGapless) }
                    ))
                }
                // Automix toggle
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.music_note),
                    title = { Text("Automix") },
                    description = { Text("Crossfade gaya DJ — otomatis mencampur lagu dengan pemudaran halus 4 detik lebih awal di setiap lagu") },
                    trailingContent = {
                        Switch(
                            checked = automixEnabled,
                            onCheckedChange = onAutomixEnabledChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (automixEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onAutomixEnabledChange(!automixEnabled) }
                ))
                if (automixEnabled) {
                    add(Material3SettingsItem(
                        icon = painterResource(R.drawable.album),
                        title = { Text("Titik Campuran Automix") },
                        description = {
                            Column {
                                Text("${automixBlendPercent.roundToInt()}%")
                                Slider(
                                    value = automixBlendPercent,
                                    onValueChange = onAutomixBlendPercentChange,
                                    valueRange = 50f..100f,
                                    steps = 9
                                )
                            }
                        }
                    ))
                }
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.history),
                    title = { Text(stringResource(R.string.history_duration)) },
                    description = {
                        Column {
                            Text(historyDuration.roundToInt().toString())
                            Slider(
                                value = historyDuration,
                                onValueChange = onHistoryDurationChange,
                                valueRange = 1f..100f
                            )
                        }
                    }
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.fast_forward),
                    title = { Text(stringResource(R.string.skip_silence)) },
                    description = { Text(stringResource(R.string.skip_silence_desc)) },
                    trailingContent = {
                        Switch(
                            checked = skipSilence,
                            onCheckedChange = onSkipSilenceChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (skipSilence) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onSkipSilenceChange(!skipSilence) }
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.skip_next),
                    title = { Text(stringResource(R.string.skip_silence_instant)) },
                    description = { Text(stringResource(R.string.skip_silence_instant_desc)) },
                    trailingContent = {
                        Switch(
                            checked = skipSilenceInstant,
                            onCheckedChange = { onSkipSilenceInstantChange(it) },
                            enabled = skipSilence,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (skipSilenceInstant) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { if (skipSilence) onSkipSilenceInstantChange(!skipSilenceInstant) }
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.volume_up),
                    title = { Text(stringResource(R.string.audio_normalization)) },
                    trailingContent = {
                        Switch(
                            checked = audioNormalization,
                            onCheckedChange = onAudioNormalizationChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (audioNormalization) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onAudioNormalizationChange(!audioNormalization) }
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.volume_off_pause),
                    title = { Text(stringResource(R.string.late_night_mode)) },
                    description = { Text(stringResource(R.string.late_night_mode_desc)) },
                    trailingContent = {
                        Switch(
                            checked = lateNightMode,
                            onCheckedChange = onLateNightModeChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lateNightMode) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onLateNightModeChange(!lateNightMode) }
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.graphic_eq),
                    title = { Text(stringResource(R.string.audio_offload)) },
                    description = {
                        Text(
                            if (crossfadeEnabled) stringResource(R.string.audio_offload_disabled_by_crossfade)
                            else stringResource(R.string.audio_offload_description)
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = if (crossfadeEnabled) false else audioOffload,
                            onCheckedChange = onAudioOffloadChange,
                            enabled = !crossfadeEnabled,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (!crossfadeEnabled && audioOffload) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { if (!crossfadeEnabled) onAudioOffloadChange(!audioOffload) }
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.slow_motion_video),
                    title = { Text(stringResource(R.string.enable_video_mode)) },
                    description = { Text(stringResource(R.string.enable_video_mode_desc)) },
                    trailingContent = {
                        Switch(
                            checked = videoModeEnabled,
                            onCheckedChange = onVideoModeEnabledChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (videoModeEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onVideoModeEnabledChange(!videoModeEnabled) }
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.slow_motion_video),
                    title = { Text(stringResource(R.string.aura_canvas)) },
                    description = { Text(stringResource(R.string.aura_canvas_desc)) },
                    trailingContent = {
                        Switch(
                            checked = auraCanvasEnabled,
                            onCheckedChange = onAuraCanvasEnabledChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (auraCanvasEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                     onClick = { onAuraCanvasEnabledChange(!auraCanvasEnabled) }
                 ))
                 add(Material3SettingsItem(
                     icon = painterResource(R.drawable.ic_subtitles),
                    title = { Text(stringResource(R.string.closed_captions)) },
                    description = { Text(stringResource(R.string.closed_captions_desc)) },
                    trailingContent = {
                        Switch(
                            checked = subtitlesEnabled,
                            onCheckedChange = onSubtitlesEnabledChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (subtitlesEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onSubtitlesEnabledChange(!subtitlesEnabled) }
                ))
                // Only show Cast setting in GMS builds (not in F-Droid/FOSS)
                if (BuildConfig.CAST_AVAILABLE) {
                    add(Material3SettingsItem(
                        icon = painterResource(R.drawable.cast),
                        title = { Text(stringResource(R.string.google_cast)) },
                        description = { Text(stringResource(R.string.google_cast_description)) },
                        trailingContent = {
                            Switch(
                                checked = enableGoogleCast,
                                onCheckedChange = onEnableGoogleCastChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (enableGoogleCast) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onEnableGoogleCastChange(!enableGoogleCast) }
                    ))
                }
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.arrow_forward),
                    title = { Text(stringResource(R.string.seek_seconds_addup)) },
                    description = { Text(stringResource(R.string.seek_seconds_addup_description)) },
                    trailingContent = {
                        Switch(
                            checked = seekExtraSeconds,
                            onCheckedChange = onSeekExtraSeconds,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (seekExtraSeconds) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onSeekExtraSeconds(!seekExtraSeconds) }
                ))
            }
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = "SponsorBlock",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.ic_sponsor_block),
                    title = { Text("SponsorBlock") },
                    description = { Text("Lewati otomatis segmen sponsor dalam video") },
                    trailingContent = {
                        Switch(
                            checked = sponsorBlockEnabled,
                            onCheckedChange = onSponsorBlockEnabledChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (sponsorBlockEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onSponsorBlockEnabledChange(!sponsorBlockEnabled) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.check),
                    title = { Text("Lewati Sponsor") },
                    description = { Text("Promosi berbayar, belum tentu iklan") },
                    trailingContent = {
                        Switch(
                            checked = sbSkipSponsor,
                            onCheckedChange = onSbSkipSponsorChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (sbSkipSponsor) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onSbSkipSponsorChange(!sbSkipSponsor) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.check),
                    title = { Text("Lewati Promosi Mandiri") },
                    description = { Text("Promosi mandiri tidak berbayar atau konten bonus") },
                    trailingContent = {
                        Switch(
                            checked = sbSkipSelfPromo,
                            onCheckedChange = onSbSkipSelfPromoChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (sbSkipSelfPromo) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onSbSkipSelfPromoChange(!sbSkipSelfPromo) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.check),
                    title = { Text("Lewati Pengingat Interaksi") },
                    description = { Text("Pengingat subscribe, like, dan bagikan") },
                    trailingContent = {
                        Switch(
                            checked = sbSkipInteraction,
                            onCheckedChange = onSbSkipInteractionChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (sbSkipInteraction) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onSbSkipInteractionChange(!sbSkipInteraction) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.check),
                    title = { Text("Lewati Intro") },
                    description = { Text("Bagian pembuka dan animasi intro") },
                    trailingContent = {
                        Switch(
                            checked = sbSkipIntro,
                            onCheckedChange = onSbSkipIntroChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (sbSkipIntro) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onSbSkipIntroChange(!sbSkipIntro) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.check),
                    title = { Text("Lewati Outro") },
                    description = { Text("Layar akhir dan bagian penutup") },
                    trailingContent = {
                        Switch(
                            checked = sbSkipOutro,
                            onCheckedChange = onSbSkipOutroChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (sbSkipOutro) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onSbSkipOutroChange(!sbSkipOutro) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.check),
                    title = { Text("Lewati Pratinjau") },
                    description = { Text("Kilas balik dari apa yang sudah ditonton") },
                    trailingContent = {
                        Switch(
                            checked = sbSkipPreview,
                            onCheckedChange = onSbSkipPreviewChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (sbSkipPreview) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onSbSkipPreviewChange(!sbSkipPreview) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.check),
                    title = { Text("Lewati Non-Musik") },
                    description = { Text("Bagian video musik saat lagu tidak dimainkan") },
                    trailingContent = {
                        Switch(
                            checked = sbSkipMusicOffTopic,
                            onCheckedChange = onSbSkipMusicOffTopicChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (sbSkipMusicOffTopic) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onSbSkipMusicOffTopicChange(!sbSkipMusicOffTopic) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.check),
                    title = { Text("Lewati Bagian Pengisi") },
                    description = { Text("Bagian pengisi atau keluar topik") },
                    trailingContent = {
                        Switch(
                            checked = sbSkipFiller,
                            onCheckedChange = onSbSkipFillerChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (sbSkipFiller) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onSbSkipFillerChange(!sbSkipFiller) }
                ),
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.queue),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.queue_music),
                    title = { Text(stringResource(R.string.persistent_queue)) },
                    description = { Text(stringResource(R.string.persistent_queue_desc)) },
                    trailingContent = {
                        Switch(
                            checked = persistentQueue,
                            onCheckedChange = onPersistentQueueChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (persistentQueue) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onPersistentQueueChange(!persistentQueue) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.playlist_add),
                    title = { Text(stringResource(R.string.auto_load_more)) },
                    description = { Text(stringResource(R.string.auto_load_more_desc)) },
                    trailingContent = {
                        Switch(
                            checked = autoLoadMore,
                            onCheckedChange = onAutoLoadMoreChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (autoLoadMore) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onAutoLoadMoreChange(!autoLoadMore) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.repeat),
                    title = { Text(stringResource(R.string.disable_load_more_when_repeat_all)) },
                    description = { Text(stringResource(R.string.disable_load_more_when_repeat_all_desc)) },
                    trailingContent = {
                        Switch(
                            checked = disableLoadMoreWhenRepeatAll,
                            onCheckedChange = onDisableLoadMoreWhenRepeatAllChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (disableLoadMoreWhenRepeatAll) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onDisableLoadMoreWhenRepeatAllChange(!disableLoadMoreWhenRepeatAll) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.download),
                    title = { Text(stringResource(R.string.auto_download_on_like)) },
                    description = { Text(stringResource(R.string.auto_download_on_like_desc)) },
                    trailingContent = {
                        Switch(
                            checked = autoDownloadOnLike,
                            onCheckedChange = onAutoDownloadOnLikeChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (autoDownloadOnLike) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onAutoDownloadOnLikeChange(!autoDownloadOnLike) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.similar),
                    title = { Text(stringResource(R.string.enable_similar_content)) },
                    description = { Text(stringResource(R.string.similar_content_desc)) },
                    trailingContent = {
                        Switch(
                            checked = similarContentEnabled,
                            onCheckedChange = similarContentEnabledChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (similarContentEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { similarContentEnabledChange(!similarContentEnabled) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.shuffle),
                    title = { Text(stringResource(R.string.persistent_shuffle_title)) },
                    description = { Text(stringResource(R.string.persistent_shuffle_desc)) },
                    trailingContent = {
                        Switch(
                            checked = persistentShuffleAcrossQueues,
                            onCheckedChange = onPersistentShuffleAcrossQueuesChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (persistentShuffleAcrossQueues) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onPersistentShuffleAcrossQueuesChange(!persistentShuffleAcrossQueues) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.shuffle),
                    title = { Text(stringResource(R.string.remember_shuffle_and_repeat)) },
                    description = { Text(stringResource(R.string.remember_shuffle_and_repeat_desc)) },
                    trailingContent = {
                        Switch(
                            checked = rememberShuffleAndRepeat,
                            onCheckedChange = onRememberShuffleAndRepeatChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (rememberShuffleAndRepeat) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onRememberShuffleAndRepeatChange(!rememberShuffleAndRepeat) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.shuffle),
                    title = { Text(stringResource(R.string.shuffle_playlist_first)) },
                    description = { Text(stringResource(R.string.shuffle_playlist_first_desc)) },
                    trailingContent = {
                        Switch(
                            checked = shufflePlaylistFirst,
                            onCheckedChange = onShufflePlaylistFirstChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (shufflePlaylistFirst) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onShufflePlaylistFirstChange(!shufflePlaylistFirst) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.skip_next),
                    title = { Text(stringResource(R.string.auto_skip_next_on_error)) },
                    description = { Text(stringResource(R.string.auto_skip_next_on_error_desc)) },
                    trailingContent = {
                        Switch(
                            checked = autoSkipNextOnError,
                            onCheckedChange = onAutoSkipNextOnErrorChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (autoSkipNextOnError) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onAutoSkipNextOnErrorChange(!autoSkipNextOnError) }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.misc),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.clear_all),
                    title = { Text(stringResource(R.string.stop_music_on_task_clear)) },
                    trailingContent = {
                        Switch(
                            checked = stopMusicOnTaskClear,
                            onCheckedChange = onStopMusicOnTaskClearChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (stopMusicOnTaskClear) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onStopMusicOnTaskClearChange(!stopMusicOnTaskClear) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.volume_off_pause),
                    title = { Text(stringResource(R.string.pause_music_when_media_is_muted)) },
                    trailingContent = {
                        Switch(
                            checked = pauseOnMute,
                            onCheckedChange = onPauseOnMuteChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (pauseOnMute) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onPauseOnMuteChange(!pauseOnMute) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.screenshot),
                    title = { Text(stringResource(R.string.keep_screen_on_when_player_is_expanded)) },
                    trailingContent = {
                        Switch(
                            checked = keepScreenOn,
                            onCheckedChange = onKeepScreenOnChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (keepScreenOn) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onKeepScreenOnChange(!keepScreenOn) }
                )
            )
        )
        Spacer(modifier = Modifier.height(27.dp))

        // Voice Command Settings
        var showWakeWordDialog by rememberSaveable { mutableStateOf(false) }

        Material3SettingsGroup(
            title = stringResource(R.string.voice_commands),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.mic),
                    title = { Text(stringResource(R.string.enable_voice_commands)) },
                    description = { Text(stringResource(R.string.enable_voice_commands_desc)) },
                    trailingContent = {
                        Switch(
                            checked = enableVoiceCommands,
                            onCheckedChange = onEnableVoiceCommandsChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (enableVoiceCommands) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onEnableVoiceCommandsChange(!enableVoiceCommands) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.mic),
                    title = { Text(stringResource(R.string.enable_voice_wake_word)) },
                    description = { Text(stringResource(R.string.enable_voice_wake_word_desc)) },
                    trailingContent = {
                        Switch(
                            checked = enableVoiceWakeWord,
                            onCheckedChange = onEnableVoiceWakeWordChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (enableVoiceWakeWord) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onEnableVoiceWakeWordChange(!enableVoiceWakeWord) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.mic),
                    title = { Text(stringResource(R.string.voice_wake_word)) },
                    description = { Text(voiceWakeWord) },
                    onClick = { showWakeWordDialog = true }
                )
            )
        )

        // Voice Feedback Settings
        val voiceFeedbackViewModel: VoiceFeedbackSettingsViewModel = hiltViewModel()
        var showVoiceDialog by rememberSaveable { mutableStateOf(false) }
        var showPitchDialog by rememberSaveable { mutableStateOf(false) }
        var showRateDialog by rememberSaveable { mutableStateOf(false) }
        var tempPitch by remember { mutableFloatStateOf(voiceFeedbackViewModel.pitch.value) }
        var tempRate by remember { mutableFloatStateOf(voiceFeedbackViewModel.speechRate.value) }

        LaunchedEffect(showPitchDialog) {
            if (showPitchDialog) {
                tempPitch = voiceFeedbackViewModel.pitch.value
            }
        }
        LaunchedEffect(showRateDialog) {
            if (showRateDialog) {
                tempRate = voiceFeedbackViewModel.speechRate.value
            }
        }

        Material3SettingsGroup(
            title = stringResource(R.string.voice_feedback),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.mic),
                    title = { Text(stringResource(R.string.enable_voice_feedback)) },
                    description = { Text(stringResource(R.string.enable_voice_feedback_desc)) },
                    trailingContent = {
                        Switch(
                            checked = voiceFeedbackViewModel.isEnabled.value,
                            onCheckedChange = { voiceFeedbackViewModel.setEnabled(it) },
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (voiceFeedbackViewModel.isEnabled.value) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { voiceFeedbackViewModel.setEnabled(!voiceFeedbackViewModel.isEnabled.value) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.mic),
                    title = { Text(stringResource(R.string.assistant_voice)) },
                    description = {
                        Text(voiceFeedbackViewModel.selectedVoice.value?.locale?.displayName ?: stringResource(R.string.default_))
                    },
                    onClick = { showVoiceDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.mic),
                    title = { Text(stringResource(R.string.voice_pitch)) },
                    description = { Text("${(voiceFeedbackViewModel.pitch.value * 100).roundToInt()}%") },
                    onClick = { showPitchDialog = true }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.mic),
                    title = { Text(stringResource(R.string.voice_speech_rate)) },
                    description = { Text("${(voiceFeedbackViewModel.speechRate.value * 100).roundToInt()}%") },
                    onClick = { showRateDialog = true }
                )
            )
        )

        // Wake word dialog
        if (showWakeWordDialog) {
            var tempWakeWord by remember { mutableStateOf(voiceWakeWord) }
            
            DefaultDialog(
                onDismiss = { 
                    tempWakeWord = voiceWakeWord
                    showWakeWordDialog = false 
                },
                buttons = {
                    TextButton(
                        onClick = { 
                            tempWakeWord = "Aura"
                        }
                    ) {
                        Text(stringResource(R.string.reset))
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    TextButton(
                        onClick = { 
                            tempWakeWord = voiceWakeWord
                            showWakeWordDialog = false 
                        }
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    TextButton(
                        onClick = { 
                            if (tempWakeWord.isNotBlank()) {
                                onVoiceWakeWordChange(tempWakeWord.trim())
                            }
                            showWakeWordDialog = false 
                        }
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.voice_wake_word),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Text(
                        text = "Saat ini: $tempWakeWord",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = "Ucapkan \"OK Aura\", \"Hey Aura\", atau \"Halo Aura\" untuk mengaktifkan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Voice selection dialog
        if (showVoiceDialog) {
            val voices by voiceFeedbackViewModel.availableVoices
            if (voices.isNotEmpty()) {
                val currentVoice = voiceFeedbackViewModel.selectedVoice.value ?: voices.first()
                EnumDialog(
                    onDismiss = { showVoiceDialog = false },
                    onSelect = { voice ->
                        voiceFeedbackViewModel.setVoice(voice)
                        showVoiceDialog = false
                    },
                    title = stringResource(R.string.assistant_voice),
                    current = currentVoice,
                    values = voices,
                    valueText = { voice: android.speech.tts.Voice -> voice.locale.displayName }
                )
            } else {
                DefaultDialog(
                    onDismiss = { showVoiceDialog = false },
                    buttons = {}
                ) {
                    Text(text = "Tidak ada suara TTS yang terpasang. Pasang mesin TTS dari Play Store.")
                }
            }
        }

        // Pitch dialog
        if (showPitchDialog) {
            DefaultDialog(
                onDismiss = { showPitchDialog = false },
                buttons = {
                    TextButton(onClick = { tempPitch = 1.0f }) { Text(stringResource(R.string.reset)) }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { showPitchDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                    TextButton(onClick = {
                        voiceFeedbackViewModel.setPitch(tempPitch)
                        showPitchDialog = false
                    }) { Text(stringResource(android.R.string.ok)) }
                }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.voice_pitch), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                    Text(text = "${(tempPitch * 100).roundToInt()}%", modifier = Modifier.padding(bottom = 8.dp))
                    Slider(value = tempPitch, onValueChange = { tempPitch = it }, valueRange = 0.5f..2.0f, steps = 15, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Speech rate dialog
        if (showRateDialog) {
            DefaultDialog(
                onDismiss = { showRateDialog = false },
                buttons = {
                    TextButton(onClick = { tempRate = 1.0f }) { Text(stringResource(R.string.reset)) }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { showRateDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                    TextButton(onClick = {
                        voiceFeedbackViewModel.setSpeechRate(tempRate)
                        showRateDialog = false
                    }) { Text(stringResource(android.R.string.ok)) }
                }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.voice_speech_rate), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                    Text(text = "${(tempRate * 100).roundToInt()}%", modifier = Modifier.padding(bottom = 8.dp))
                    Slider(value = tempRate, onValueChange = { tempRate = it }, valueRange = 0.5f..2.0f, steps = 15, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.player_and_audio)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )
}
