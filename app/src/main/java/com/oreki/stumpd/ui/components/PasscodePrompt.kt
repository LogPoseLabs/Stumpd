package com.oreki.stumpd.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Asks for a passcode and reports whether it was right.
 *
 * Knows nothing about *which* passcode: verification is a lambda, so the same dialog gates
 * deleting a player, editing a player, and correcting a match. [title] exists because there are
 * now two different passwords in the app, and "Enter passcode" alone leaves the user guessing
 * which one is wanted.
 */
@Composable
fun PasscodePrompt(
    onVerify: suspend (String) -> Boolean,
    onPasscodeCorrect: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "Enter passcode",
    label: String = "Passcode",
    body: String? = null,
) {
    val scope = rememberCoroutineScope()
    var entered by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (body != null) {
                    Text(body, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = entered,
                    onValueChange = {
                        entered = it
                        showError = false
                    },
                    label = { Text(label) },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text("That passcode doesn't match") }
                    } else null,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    checking = true
                    scope.launch {
                        val ok = onVerify(entered)
                        checking = false
                        if (ok) onPasscodeCorrect() else showError = true
                    }
                },
                // Any non-empty passcode: there is no length or character rule.
                enabled = entered.isNotEmpty() && !checking,
            ) {
                if (checking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Unlock")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
