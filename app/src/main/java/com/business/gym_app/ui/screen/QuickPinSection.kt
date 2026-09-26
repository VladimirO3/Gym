package com.business.gym_app.ui.screen

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.business.gym_app.R
import com.business.gym_app.ui.viewmodel.AuthViewModel
import com.business.gym_app.util.PinHelper

/** Секция профиля: создание / смена быстрого PIN-кода. */
@Composable
fun QuickPinSection(
    contentModifier: Modifier,
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current
    val pinError by authViewModel.error
    var pinSet by remember { mutableStateOf(PinHelper.isPinSet(context)) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    // Срок PIN — 7 дней. Показываем, сколько дней осталось / что пора менять.
    var pinExpired by remember { mutableStateOf(PinHelper.isPinExpired(context)) }
    var pinDaysLeft by remember { mutableStateOf(PinHelper.pinDaysLeft(context)) }
    val pinValue by authViewModel.pinValue
    val pinConfirm by authViewModel.pinConfirm
    val pinResetDone = stringResource(R.string.pin_reset_done)
    val pinSavedText = stringResource(R.string.pin_saved)
    val currentAccount = authViewModel.currentUserEmail.value
        ?: authViewModel.email.value.ifBlank { authViewModel.otpEmail.value }

    Card(
        modifier = contentModifier,
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.2f)),
        border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Red, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.pin_title), style = MaterialTheme.typography.bodyLarge, color = Color.White)
                    Text(
                        when {
                            !pinSet -> stringResource(R.string.pin_not_set)
                            pinExpired -> stringResource(R.string.pin_expired_status)
                            else -> stringResource(R.string.pin_days_left, pinDaysLeft.toInt())
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (pinSet && pinExpired) Color.Red else Color.Gray
                    )
                }
            }
            Button(
                onClick = {
                    if (pinSet && !pinExpired) {
                        showResetConfirm = true
                    } else {
                        // PIN не задан или срок 7 дней вышел — создаём/меняем PIN.
                        authViewModel.requestPinSetup(
                            currentAccount.orEmpty(),
                            if (pinExpired) AuthViewModel.PinChangeReason.EXPIRED else AuthViewModel.PinChangeReason.MANUAL
                        )
                        showPinDialog = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    stringResource(if (pinSet) R.string.pin_change else R.string.pin_create),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showPinDialog) {
        val pinChangeReason by authViewModel.pinChangeReason
        PinEditDialog(
            pinValue = pinValue,
            pinConfirm = pinConfirm,
            pinError = pinError,
            onPinChange = { authViewModel.onPinChange(it) },
            onPinConfirmChange = { authViewModel.onPinConfirmChange(it) },
            onDismiss = {
                showPinDialog = false
                // В профиле смену PIN можно отложить («Позже») — срок проверим при входе.
                authViewModel.dismissPinSetup()
            },
            onSave = {
                authViewModel.savePinCode(currentAccount.orEmpty()) {
                    pinSet = true
                    pinExpired = PinHelper.isPinExpired(context)
                    pinDaysLeft = PinHelper.pinDaysLeft(context)
                    showPinDialog = false
                    Toast.makeText(context, pinSavedText, Toast.LENGTH_SHORT).show()
                }
            },
            dismissTextRes = if (pinExpired) R.string.pin_change_later else R.string.cancel,
            titleRes = if (pinChangeReason == AuthViewModel.PinChangeReason.MANUAL) R.string.pin_create_title else R.string.pin_change_title,
            subtitleRes = if (pinChangeReason == AuthViewModel.PinChangeReason.MANUAL) R.string.pin_create_subtitle else R.string.pin_change_subtitle,
            dismissible = true
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.pin_reset_title)) },
            text = { Text(stringResource(R.string.pin_reset_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        authViewModel.clearPinCode()
                        pinSet = false
                        showResetConfirm = false
                        Toast.makeText(context, pinResetDone, Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text(stringResource(R.string.pin_reset_confirm), color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.cancel), color = Color.Gray)
                }
            }
        )
    }
}
