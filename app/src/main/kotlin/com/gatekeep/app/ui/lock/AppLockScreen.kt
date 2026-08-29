package com.gatekeep.app.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gatekeep.app.R
import com.gatekeep.app.ui.components.PinTextField
import com.gatekeep.app.util.PasswordHasher

@Composable
fun AppLockScreen(
    passwordHash: String?,
    onUnlocked: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.enter_pin_unlock), style = MaterialTheme.typography.bodyMedium)
        PinTextField(
            value = pin,
            onValueChange = { pin = it; error = false },
            label = stringResource(R.string.pin),
            isError = error,
            requestInitialFocus = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        )
        Button(
            onClick = {
                if (!passwordHash.isNullOrBlank() && PasswordHasher.verify(pin, passwordHash)) {
                    onUnlocked()
                } else {
                    error = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.unlock)) }
    }
}
