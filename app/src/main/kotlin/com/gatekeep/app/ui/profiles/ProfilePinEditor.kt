package com.gatekeep.app.ui.profiles



import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.padding

import androidx.compose.material3.Button

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.unit.dp

import com.gatekeep.app.R

import com.gatekeep.app.ui.components.PinTextField

import com.gatekeep.app.ui.viewmodel.ProfileViewModel

import com.gatekeep.app.util.PasswordHasher

import com.gatekeep.domain.model.Profile



@Composable

fun ProfilePinEditor(

    pin: String,

    savedPin: String,

    onPinChange: (String) -> Unit,

    label: String,

    hint: String? = null,

    clearHint: String? = null,

    modifier: Modifier = Modifier,

    profile: Profile?,

    viewModel: ProfileViewModel,

    pinGateActive: Boolean,

    onSaved: (String) -> Unit,

) {

    val pinDirty = pin.trim() != savedPin.trim()



    fun savePin() {

        profile?.let { p ->

            val trimmed = pin.trim()

            if (trimmed.isNotBlank()) {

                viewModel.saveProfilePin(p.id, trimmed)

                viewModel.saveProfile(

                    p.copy(

                        passwordHash = PasswordHasher.hash(trimmed),

                        lockEnabled = pinGateActive,

                    ),

                )

                onSaved(trimmed)

            } else {

                viewModel.clearProfilePin(p.id)

                viewModel.saveProfile(p.copy(passwordHash = null, lockEnabled = false))

                onSaved("")

            }

        }

    }



    Column(modifier = modifier.fillMaxWidth()) {

        PinTextField(

            value = pin,

            onValueChange = onPinChange,

            label = label,

        )

        hint?.let {

            Text(

                it,

                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,

                modifier = Modifier.padding(top = 4.dp),

            )

        }

        if (pinDirty) {

            Button(

                onClick = { savePin() },

                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),

            ) { Text(stringResource(R.string.save_profile_pin)) }

        }

        if (pin.isBlank() && clearHint != null) {

            Text(

                clearHint,

                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,

                modifier = Modifier.padding(top = 4.dp),

            )

        }

    }

}


