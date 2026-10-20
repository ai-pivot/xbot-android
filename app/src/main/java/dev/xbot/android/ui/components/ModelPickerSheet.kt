package dev.xbot.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.xbot.android.data.ModelEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    title: String,
    models: List<ModelEntry>,
    currentSubId: String,
    currentModel: String,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (ModelEntry) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(4.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (models.isEmpty() && !loading) {
                Text(
                    "没有可用模型。请先在 Web 设置或 CLI 中配置订阅。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(models, key = { "${it.subId}:${it.model}" }) { entry ->
                    val selected = entry.model == currentModel &&
                        (currentSubId.isBlank() || entry.subId == currentSubId)
                    ListItem(
                        headlineContent = {
                            Text(
                                entry.model,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            if (entry.subtitle.isNotBlank()) {
                                Text(
                                    entry.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        },
                        trailingContent = {
                            if (selected) {
                                Icon(Icons.Filled.Check, contentDescription = "当前")
                            }
                        },
                        modifier = Modifier.clickable(enabled = entry.selectable) {
                            if (entry.selectable) onSelect(entry)
                        },
                    )
                }
            }
        }
    }
}
