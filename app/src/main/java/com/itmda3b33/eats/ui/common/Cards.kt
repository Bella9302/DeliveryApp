package com.itmda3b33.eats.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The rounded grey card used for list rows, as in the restaurant and menu designs. */
@Composable
fun EatsCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val shape = RoundedCornerShape(12.dp)
    if (onClick != null) {
        Card(onClick = onClick, colors = colors, shape = shape, modifier = modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(colors = colors, shape = shape, modifier = modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

/** Secondary line of text in a card. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}
