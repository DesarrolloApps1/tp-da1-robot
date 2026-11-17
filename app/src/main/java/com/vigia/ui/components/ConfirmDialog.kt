package com.vigia.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.vigia.ui.theme.VigiaTheme

// Confirmación para órdenes que no conviene mandar por un toque accidental
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Descartar",
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

@Preview
@Composable
private fun ConfirmDialogPreview() {
    VigiaTheme {
        ConfirmDialog(
            title = "¿Cancelar la misión?",
            message = "El robot deja la ronda y queda listo donde está.",
            confirmLabel = "Cancelar misión",
            onConfirm = {},
            onDismiss = {},
            destructive = true,
        )
    }
}
