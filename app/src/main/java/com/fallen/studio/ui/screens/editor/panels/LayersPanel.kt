package com.fallen.studio.ui.screens.editor.panels

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fallen.studio.data.model.CanvasElement
import com.fallen.studio.util.ImageUtils

/**
 * Панель «Слои»: список элементов холста, верхний слой — сверху.
 * Действия: выбор, блокировка, дублирование, изменение порядка, удаление.
 */
@Composable
fun LayersPanel(
    elements: List<CanvasElement>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onToggleLock: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onMoveLayer: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRename: (String, String) -> Unit = { _, _ -> },
    onToggleDimensions: (String) -> Unit = {},
    /** Глобальная настройка «Размерные метки» включена? */
    dimensionMarksEnabled: Boolean = true,
) {
    // Верхний слой (максимальный z) показываем первым
    val ordered = elements.sortedByDescending { it.z }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Слои",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${elements.size} элементов",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (ordered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Слоёв пока нет",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Добавьте ассет или текст на холст",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(ordered, key = { it.id }) { element ->
                    LayerRow(
                        element = element,
                        isSelected = element.id == selectedId,
                        onSelect = { onSelect(element.id) },
                        onToggleLock = { onToggleLock(element.id) },
                        onDuplicate = { onDuplicate(element.id) },
                        onMoveUp = { onMoveLayer(element.id, true) },
                        onMoveDown = { onMoveLayer(element.id, false) },
                        onDelete = { onDelete(element.id) },
                        onRename = { newName -> onRename(element.id, newName) },
                        onToggleDimensions = { onToggleDimensions(element.id) },
                        dimensionMarksEnabled = dimensionMarksEnabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun LayerRow(
    element: CanvasElement,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleLock: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onRename: (String) -> Unit = {},
    onToggleDimensions: () -> Unit = {},
    dimensionMarksEnabled: Boolean = true,
) {
    // Режим редактирования названия слоя
    var editing by remember(element.id) { mutableStateOf(false) }
    var nameInput by remember(element.id, element.name) { mutableStateOf(element.name) }
    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .border(width = if (isSelected) 1.5.dp else 1.dp, color = borderColor, shape = shape)
            .clickable(onClick = onSelect)
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Превью слоя
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                val bitmap: Bitmap? = if (element.isImage && element.src != null) {
                    remember(element.id, element.src) { ImageUtils.decodeDataUrl(element.src) }
                } else null
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = element.name,
                        modifier = Modifier.size(40.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Название (тап по карандашу — переименовать), тип и размеры
            Column(modifier = Modifier.weight(1f)) {
                if (editing) {
                    BasicTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it.take(40) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardActions = KeyboardActions(onDone = {
                            if (nameInput.isNotBlank()) onRename(nameInput)
                            editing = false
                        }),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(6.dp),
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                } else {
                    Text(
                        text = element.name.ifBlank {
                            if (element.isText) (element.text ?: "Текст").take(20) else "Слой"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (element.isText) "текст" else "изображение",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "${element.w.toInt()} × ${element.h.toInt()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Действия
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            // Переименование слоя
            IconButton(
                onClick = {
                    if (editing && nameInput.isNotBlank()) onRename(nameInput)
                    editing = !editing
                },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = if (editing) Icons.Outlined.Check else Icons.Outlined.Edit,
                    contentDescription = if (editing) "Сохранить название" else "Переименовать",
                    tint = if (editing) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            // Размерные метки этого элемента (только при включённой настройке)
            IconButton(
                onClick = onToggleDimensions,
                enabled = dimensionMarksEnabled,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Straighten,
                    contentDescription = if (element.showDimensions)
                        "Скрыть размерные метки" else "Показать размерные метки",
                    tint = when {
                        !dimensionMarksEnabled ->
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        element.showDimensions -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDuplicate, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Дублировать",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onToggleLock, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = if (element.locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                    contentDescription = if (element.locked) "Разблокировать" else "Заблокировать",
                    tint = if (element.locked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onMoveUp, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Outlined.ArrowUpward,
                    contentDescription = "Слой выше",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onMoveDown, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Outlined.ArrowDownward,
                    contentDescription = "Слой ниже",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
