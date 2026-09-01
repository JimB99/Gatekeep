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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.gatekeep.app.R

@Composable
fun SaveChangesButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.save_changes),
    enabled: Boolean = true,
) {
    if (!visible) return
    Button(
        onClick = onClick,
        enabled = enabled,
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
    onSave: suspend () -> Unit,
    onDiscardChanges: () -> Unit,
): UnsavedChangesGuardState {
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isDirtyState = rememberUpdatedState(isDirty)
    val onNavigateBackState = rememberUpdatedState(onNavigateBack)
    val onSaveState = rememberUpdatedState(onSave)
    val onDiscardChangesState = rememberUpdatedState(onDiscardChanges)

    val state = remember {
        UnsavedChangesGuardState {
            if (isDirtyState.value) {
                showDialog = true
            } else {
                onNavigateBackState.value()
            }
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
                    scope.launch {
                        onSaveState.value()
                        onNavigateBackState.value()
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                    onDiscardChangesState.value()
                    onNavigateBackState.value()
                }) { Text(stringResource(R.string.discard)) }
            },
        )
    }

    return state
}
