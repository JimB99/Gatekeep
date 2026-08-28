package com.gatekeep.app.ui.settings

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.gatekeep.app.R
import com.gatekeep.app.ui.viewmodel.SettingsViewModel
import com.gatekeep.app.util.LocaleController
import kotlinx.coroutines.launch

@Composable
fun LanguageSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    SettingsDetailScaffold(
        title = stringResource(R.string.language),
        onBack = onBack,
    ) {
        LocaleController.supportedTags.forEach { tag ->
            val selected = settings.languageTag == tag
            ListItem(
                headlineContent = {
                    Text(
                        "${LocaleController.flagForTag(tag)} ${LocaleController.nativeLabelForTag(tag)}",
                    )
                },
                leadingContent = {
                    RadioButton(
                        selected = selected,
                        onClick = null,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !selected) {
                        scope.launch {
                            viewModel.setLanguage(tag)
                            activity?.recreate()
                        }
                    },
            )
        }
    }
}
