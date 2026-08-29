package com.gatekeep.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.gatekeep.app.R

@Composable
fun SaveChangesButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.save_changes),
) {
    if (!visible) return
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) { Text(label) }
}

class UnsavedChangesGuardState internal constructor(
    private val requestBack: () -> Unit,
) {
    fun navigateBack() = requestBack()
}

@Composable
fun rememberUnsavedChangesGuard(
    isDirty: Boolean,
    onNavigateBack: () -> Unit,
    onSave: () -> Unit,
    onDiscardChanges: () -> Unit,
): UnsavedChangesGuardState {
    var showDialog by remember { mutableStateOf(false) }

    fun attemptBack() {
        if (isDirty) {
            showDialog = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler(enabled = isDirty) {
        showDialog = true
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.unsaved_changes_title)) },
            text = { Text(stringResource(R.string.unsaved_changes_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onSave()
                    onNavigateBack()
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                    onDiscardChanges()
                    onNavigateBack()
                }) { Text(stringResource(R.string.discard)) }
            },
        )
    }

    return remember { UnsavedChangesGuardState(::attemptBack) }
}
