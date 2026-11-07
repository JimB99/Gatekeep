package com.gatekeep.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.gatekeep.app.R

@Composable
fun LegalLinkListItem(
    labelRes: Int,
    url: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(stringResource(labelRes)) },
        modifier = modifier.clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        },
    )
}
