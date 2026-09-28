/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nanzbeatles.nanas.App
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountSettingsViewModel @Inject constructor() : ViewModel() {

    /**
     * Logout user without clearing local library metadata. The next login/sync will update
     * account-backed items, while downloaded/cached/subscribed content remains visible locally.
     */
    fun logoutAndClearSyncedContent(context: Context, onCookieChange: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            App.forgetAccount(context)
            
            // Clear cookie in UI
            onCookieChange("")
        }
    }
}
