/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nanzbeatles.nanas.constants.NewReleaseNotificationsEnabledKey
import com.nanzbeatles.nanas.db.MusicDatabase
import com.nanzbeatles.nanas.utils.dataStore
import com.nanzbeatles.nanas.utils.get
import com.nanzbeatles.nanas.utils.reportException
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@AndroidEntryPoint
class NewReleaseNotificationReceiver : BroadcastReceiver() {
    @Inject
    lateinit var database: MusicDatabase

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (context.dataStore.get(NewReleaseNotificationsEnabledKey, true)) {
                NewReleaseNotificationScheduler.schedule(context.applicationContext)
            }
            return
        }

        if (intent.action != NewReleaseNotificationScheduler.ACTION_CHECK_NEW_RELEASES) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(20_000) {
                    NewReleaseNotificationChecker.check(context.applicationContext, database)
                }
            } catch (throwable: Throwable) {
                reportException(throwable)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
