package com.fallen.studio.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fallen.studio.data.ThemeMode
import com.fallen.studio.ui.components.FallenLogoMark
import com.fallen.studio.ui.components.PanelSectionTitle
import com.fallen.studio.ui.components.SliderRow
import com.fallen.studio.ui.components.SwitchRow
import com.fallen.studio.ui.theme.FallenTheme

private enum class SettingsTab(val title: String, val icon: ImageVector) {
    APPEARANCE("Внешний вид", Icons.Outlined.Brush),
    CANVAS("Канвас", Icons.Outlined.GridOn),
    EDITOR("Редактор", Icons.Outlined.Tune),
    EXPORT("Экспорт", Icons.Outlined.Code),
    ABOUT("О приложении", Icons.Outlined.Info),
}

/**
 * Экран настроек Fallen с вкладками (пункт 3 требований):
 * Внешний вид / Канвас / Редактор / Экспорт / О приложении.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val colors = FallenTheme.colors
    var tab by remember { mutableStateOf(SettingsTab.APPEARANCE) }

    Scaffold(containerColor = colors.appBackground) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.appBackground)
                .padding(padding)
                .statusBarsPadding(),
        ) {
            // Шапка
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = "Настройки",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Вкладки
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsTab.entries.forEach { t ->
                    val active = t == tab
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                else colors.surfaceElevated,
                            )
                            .border(
                                1.dp,
                                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else colors.divider,
                                RoundedCornerShape(12.dp),
                            )
                            .clickable { tab = t }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            t.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (active) MaterialTheme.colorScheme.primary else colors.textSecondary,
                        )
                        Text(
                            text = t.title,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (active) MaterialTheme.colorScheme.primary else colors.textSecondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Содержимое вкладки
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (tab) {
                    SettingsTab.APPEARANCE -> appearanceTab(settings.themeMode, viewModel)
                    SettingsTab.CANVAS -> canvasTab(settings, viewModel)
                    SettingsTab.EDITOR -> editorTab(settings, viewModel)
                    SettingsTab.EXPORT -> exportTab(settings, viewModel)
                    SettingsTab.ABOUT -> aboutTab()
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

// ==================================================================
// Вкладка «Внешний вид»
// ==================================================================

private fun androidx.compose.foundation.lazy.LazyListScope.appearanceTab(
    themeMode: ThemeMode,
    vm: SettingsViewModel,
) {
    item { PanelSectionTitle("Тема приложения") }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeOption(
                title = "Тёмная",
                subtitle = "Фирменный тёмный стиль Fallen",
                selected = themeMode == ThemeMode.DARK,
                onClick = { vm.setThemeMode(ThemeMode.DARK) },
            )
            ThemeOption(
                title = "Светлая",
                subtitle = "Светлый интерфейс для яркого освещения",
                selected = themeMode == ThemeMode.LIGHT,
                onClick = { vm.setThemeMode(ThemeMode.LIGHT) },
            )
            ThemeOption(
                title = "Как в системе",
                subtitle = "Следовать настройкам Android",
                selected = themeMode == ThemeMode.SYSTEM,
                onClick = { vm.setThemeMode(ThemeMode.SYSTEM) },
            )
        }
    }
}

@Composable
private fun ThemeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = FallenTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else colors.divider,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(text = subtitle, fontSize = 12.sp, color = colors.textSecondary)
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(2.dp, colors.divider, CircleShape),
            )
        }
    }
}

// ==================================================================
// Вкладка «Канвас»
// ==================================================================

private fun androidx.compose.foundation.lazy.LazyListScope.canvasTab(
    settings: com.fallen.studio.data.AppSettings,
    vm: SettingsViewModel,
) {
    item { PanelSectionTitle("Сетка") }
    item {
        SwitchRow(
            label = "Показывать сетку",
            sublabel = "Ровная квадратная сетка на холсте",
            checked = settings.gridEnabled,
            onCheckedChange = vm::setGridEnabled,
        )
    }
    item {
        SliderRow(
            label = "Размер ячейки сетки",
            value = settings.gridSize.toFloat(),
            range = 10f..200f,
            valueLabel = { "${it.toInt()} px" },
            onValueChange = { vm.setGridSize(it.toInt()) },
        )
    }
    item {
        SwitchRow(
            label = "Привязка к сетке",
            sublabel = "Элементы прилипают к линиям сетки при перемещении",
            checked = settings.snapToGrid,
            onCheckedChange = vm::setSnapToGrid,
        )
    }
    item { PanelSectionTitle("Направляющие") }
    item {
        SwitchRow(
            label = "Линейки",
            sublabel = "Шкала координат по краям холста",
            checked = settings.showRulers,
            onCheckedChange = vm::setShowRulers,
        )
    }
    item {
        SwitchRow(
            label = "Центральные направляющие",
            sublabel = "Пунктирные линии по центру холста",
            checked = settings.showCenterGuides,
            onCheckedChange = vm::setShowCenterGuides,
        )
    }
    item { PanelSectionTitle("Фон холста") }
    item {
        SwitchRow(
            label = "Шахматный фон",
            sublabel = "Показывает прозрачность как в графических редакторах",
            checked = settings.checkerboardBackground,
            onCheckedChange = vm::setCheckerboardBackground,
        )
    }
    item {
        SwitchRow(
            label = "Затемнять область вне холста",
            sublabel = "Холст сильнее выделяется на фоне",
            checked = settings.canvasDimOutside,
            onCheckedChange = vm::setCanvasDimOutside,
        )
    }
}

// ==================================================================
// Вкладка «Редактор»
// ==================================================================

private fun androidx.compose.foundation.lazy.LazyListScope.editorTab(
    settings: com.fallen.studio.data.AppSettings,
    vm: SettingsViewModel,
) {
    item { PanelSectionTitle("Автосохранение") }
    item {
        SwitchRow(
            label = "Автосохранение",
            sublabel = "Автоматически сохранять черновик проекта",
            checked = settings.autosaveEnabled,
            onCheckedChange = vm::setAutosaveEnabled,
        )
    }
    item {
        SliderRow(
            label = "Интервал автосохранения",
            value = settings.autosaveIntervalSec.toFloat(),
            range = 10f..300f,
            valueLabel = { "${it.toInt()} сек" },
            onValueChange = { vm.setAutosaveInterval(it.toInt()) },
        )
    }
    item { PanelSectionTitle("История изменений") }
    item {
        SliderRow(
            label = "Глубина отката (undo)",
            value = settings.undoLimit.toFloat(),
            range = 5f..200f,
            valueLabel = { "${it.toInt()} шагов" },
            onValueChange = { vm.setUndoLimit(it.toInt()) },
        )
    }
    item { PanelSectionTitle("Холст по умолчанию") }
    item {
        DefaultCanvasEditor(
            w = settings.defaultCanvasW,
            h = settings.defaultCanvasH,
            onApply = vm::setDefaultCanvas,
        )
    }
    item { PanelSectionTitle("Поведение") }
    item {
        SwitchRow(
            label = "Виброотклик",
            sublabel = "Вибрация при выделении и привязке",
            checked = settings.hapticFeedback,
            onCheckedChange = vm::setHapticFeedback,
        )
    }
    item {
        SwitchRow(
            label = "Подсказка размеров",
            sublabel = "Показывать размер элемента при изменении",
            checked = settings.showSizeTooltip,
            onCheckedChange = vm::setShowSizeTooltip,
        )
    }
    item {
        SwitchRow(
            label = "Сохранять пропорции",
            sublabel = "По умолчанию при изменении размера",
            checked = settings.keepAspectDefault,
            onCheckedChange = vm::setKeepAspectDefault,
        )
    }
}

@Composable
private fun DefaultCanvasEditor(
    w: Int,
    h: Int,
    onApply: (Int, Int) -> Unit,
) {
    val colors = FallenTheme.colors
    var wText by remember(w) { mutableStateOf(w.toString()) }
    var hText by remember(h) { mutableStateOf(h.toString()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.divider, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Размер нового холста",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = wText,
                onValueChange = { v ->
                    wText = v.filter { it.isDigit() }
                    val newW = wText.toIntOrNull()
                    val newH = hText.toIntOrNull()
                    if (newW != null && newH != null && newW >= 100 && newH >= 100) {
                        onApply(newW, newH)
                    }
                },
                label = { Text("Ширина") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = hText,
                onValueChange = { v ->
                    hText = v.filter { it.isDigit() }
                    val newW = wText.toIntOrNull()
                    val newH = hText.toIntOrNull()
                    if (newW != null && newH != null && newW >= 100 && newH >= 100) {
                        onApply(newW, newH)
                    }
                },
                label = { Text("Высота") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ==================================================================
// Вкладка «Экспорт»
// ==================================================================

private fun androidx.compose.foundation.lazy.LazyListScope.exportTab(
    settings: com.fallen.studio.data.AppSettings,
    vm: SettingsViewModel,
) {
    item { PanelSectionTitle("Формат по умолчанию") }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "unity" to "Unity C#",
                "json" to "JSON",
                "txt" to "Текст (имя + координаты)",
                "csv" to "CSV-таблица",
                "xml" to "XML",
            ).forEach { (id, label) ->
                ThemeOption(
                    title = label,
                    subtitle = when (id) {
                        "unity" -> "Готовый скрипт расстановки для Unity"
                        "json" -> "Универсальный формат для любых движков"
                        "txt" -> "Простой список: название ассета и его позиция"
                        "csv" -> "Открывается в Excel и Google Таблицах"
                        else -> "Структурированный формат для парсеров"
                    },
                    selected = settings.defaultExportFormat == id,
                    onClick = { vm.setDefaultExportFormat(id) },
                )
            }
        }
    }
    item { PanelSectionTitle("Параметры") }
    item {
        SwitchRow(
            label = "Комментарии в коде",
            sublabel = "Добавлять пояснения в экспортируемый код",
            checked = settings.includeComments,
            onCheckedChange = vm::setIncludeComments,
        )
    }
    item {
        SliderRow(
            label = "Масштаб экспорта изображения",
            value = settings.exportImageScale,
            valueRange = 0.25f..2f,
            valueLabel = { "×%.2f".format(it) },
            onValueChange = vm::setExportImageScale,
        )
    }
}

// ==================================================================
// Вкладка «О приложении»
// ==================================================================

private fun androidx.compose.foundation.lazy.LazyListScope.aboutTab() {
    item {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FallenLogoMark(size = 72.dp, glowing = true)
            Text(
                text = "Fallen",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Версия 1.0.0",
                fontSize = 13.sp,
                color = FallenTheme.colors.textSecondary,
            )
            Text(
                text = "Редактор расстановки UI-элементов и ассетов.\nСоздавайте макеты, экспортируйте координаты\nв Unity, JSON, CSV и другие форматы.",
                fontSize = 13.sp,
                color = FallenTheme.colors.textSecondary,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
    item { PanelSectionTitle("Возможности") }
    item {
        val features = listOf(
            "Импорт проектов из UI Studio Pro (.uiproj)",
            "Откат и возврат изменений (undo/redo)",
            "Тень текста, работающая вместе с обводкой",
            "Экспорт в 5 форматов + PNG-изображение",
            "Светлая и тёмная темы",
            "Автосохранение черновика",
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            features.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = f,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
