package com.itmda3b33.eats.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.itmda3b33.eats.data.model.ComplaintStatus
import com.itmda3b33.eats.data.model.OrderStatus
import com.itmda3b33.eats.data.model.RefundStatus

private val FieldShape = RoundedCornerShape(14.dp)
private val ButtonShape = RoundedCornerShape(16.dp)

/** Filled brand button. Shows a spinner and ignores taps while [loading]. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, loading: Boolean = false, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = ButtonShape,
        modifier = modifier.fillMaxWidth().height(54.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, loading: Boolean = false, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = ButtonShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth().height(54.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

/** Text link in the theme's link colour, for example "Forgot password?". */
@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleSmall)
    }
}

/** Outlined text field with its validation message underneath. */
@Composable
fun EatsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = (error ?: supportingText)?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        shape = FieldShape,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Password",
    error: String? = null,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Done,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    EatsTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        error = error,
        supportingText = supportingText,
        keyboardType = KeyboardType.Password,
        imeAction = imeAction,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Text(if (visible) "Hide" else "Show", color = MaterialTheme.colorScheme.secondary)
            }
        },
        modifier = modifier,
    )
}

/** Small coloured label for a status. */
@Composable
fun StatusPill(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val (background, foreground) = tone.colors()
    Surface(color = background, contentColor = foreground, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

enum class Tone { NEUTRAL, ACTIVE, WARNING, SUCCESS, DANGER }

@Composable
private fun Tone.colors(): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        Tone.NEUTRAL -> scheme.surfaceVariant to scheme.onSurfaceVariant
        Tone.ACTIVE -> scheme.primaryContainer to scheme.onPrimaryContainer
        Tone.WARNING -> Color(0xFFFFF1C2) to Color(0xFF6B4E00)
        Tone.SUCCESS -> Color(0xFFD7F5DD) to Color(0xFF0F5223)
        Tone.DANGER -> scheme.errorContainer to scheme.onErrorContainer
    }
}

fun OrderStatus.tone(): Tone = when (this) {
    OrderStatus.DELIVERED -> Tone.SUCCESS
    OrderStatus.CANCELLED -> Tone.DANGER
    else -> Tone.ACTIVE
}

fun ComplaintStatus.tone(): Tone = when (this) {
    ComplaintStatus.LOGGED -> Tone.NEUTRAL
    ComplaintStatus.IN_PROGRESS -> Tone.ACTIVE
    ComplaintStatus.ESCALATED -> Tone.WARNING
    ComplaintStatus.RESOLVED -> Tone.SUCCESS
}

fun RefundStatus.tone(): Tone = when (this) {
    RefundStatus.REQUESTED, RefundStatus.PROCESSING -> Tone.WARNING
    RefundStatus.APPROVED, RefundStatus.COMPLETED -> Tone.SUCCESS
    RefundStatus.REJECTED -> Tone.DANGER
}
