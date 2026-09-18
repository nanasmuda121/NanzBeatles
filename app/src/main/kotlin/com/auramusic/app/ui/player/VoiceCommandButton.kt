package com.auramusic.app.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.auramusic.app.R
import com.auramusic.app.voice.LocalVoiceCommandController

@Composable
fun VoiceCommandButton(
    modifier: Modifier = Modifier,
    onSearch: (String) -> Unit = {},
    onNavigate: (String) -> Unit = {},
) {
    val controller = LocalVoiceCommandController.current

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        FilledIconButton(
            onClick = { controller.requestManualActivation() },
            modifier = Modifier.size(42.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(
                painter = painterResource(R.drawable.mic),
                contentDescription = "Voice command",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
