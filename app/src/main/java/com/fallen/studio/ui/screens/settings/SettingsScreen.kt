package com.fallen.studio.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fallen.studio.R
import com.fallen.studio.data.ThemeMode
import com.fallen.studio.ui.components.FallenLogoMark
import com.fallen.studio.ui.components.PanelSectionTitle
import com.fallen.studio.ui.components.SliderRow
import com.fallen.studio.ui.components.SwitchRow
import com.fallen.studio.ui.theme.FallenTheme

private enum class SettingsSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    APPEARANCE("Внешний вид", "Тема приложения", Icons.Outlined.Brush),
    CANVAS("Холст", "Сетка, привязка, направляющие", Icons.Outlined.GridOn),
    EDITOR("Редактор", "Автосохранение, изображения, поведение", Icons.Outlined.Tune),
    EXPORT("Экспорт", "Форматы и параметры", Icons.Outlined.Code),
    ABOUT("О приложении", "С чего начать, обучение, контакты", Icons.Outlined.Info),
}

/**
 * Экран настроек Fallen в виде подменю:
 * главный список разделов -> вложенный экран раздела.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val colors = FallenTheme.colors
    var section by remember { mutableStateOf<SettingsSection?>(null) }
    var tutorialOpen by remember { mutableStateOf(false) }

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
                IconButton(onClick = {
                    if (section != null) section = null else onBack()
                }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = section?.title ?: "Настройки",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Подменю: список разделов <-> содержимое раздела
            AnimatedContent(
                targetState = section,
                transitionSpec = {
                    if (targetState != null) {
                        slideInHorizontally(tween(180)) { it } togetherWith
                            slideOutHorizontally(tween(180)) { -it }
                    } else {
                        slideInHorizontally(tween(180)) { -it } togetherWith
                            slideOutHorizontally(tween(180)) { it }
                    }
                },
                label = "settings_section",
            ) { current ->
                if (current == null) {
                    // Главный список разделов
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item { Spacer(Modifier.height(4.dp)) }
                        SettingsSection.entries.forEach { s ->
                            item {
                                SectionCard(section = s, onClick = { section = s })
                            }
                        }
                        item { Spacer(Modifier.height(32.dp)) }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        when (current) {
                            SettingsSection.APPEARANCE -> appearanceSection(settings.themeMode, viewModel)
                            SettingsSection.CANVAS -> canvasSection(settings, viewModel)
                            SettingsSection.EDITOR -> editorSection(settings, viewModel)
                            SettingsSection.EXPORT -> exportSection(settings, viewModel)
                            SettingsSection.ABOUT -> aboutSection(onOpenTutorial = { tutorialOpen = true })
                        }
                        item { Spacer(Modifier.height(32.dp)) }
                    }
                }
            }
        }
    }

    if (tutorialOpen) {
        TutorialOverlay(onClose = { tutorialOpen = false })
    }
}

@Composable
private fun SectionCard(section: SettingsSection, onClick: () -> Unit) {
    val colors = FallenTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.divider, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                section.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = section.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = section.subtitle,
                fontSize = 12.sp,
                color = colors.textSecondary,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ==================================================================
// Раздел «Внешний вид»
// ==================================================================

private fun androidx.compose.foundation.lazy.LazyListScope.appearanceSection(
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
// Раздел «Холст»
// ==================================================================

private fun androidx.compose.foundation.lazy.LazyListScope.canvasSection(
    settings: com.fallen.studio.data.AppSettings,
    vm: SettingsViewModel,
) {
    item { PanelSectionTitle("Сетка") }
    item {
        SwitchRow(
            label = "Показывать сетку",
            description = "Сетка подстраивается под разрешение холста",
            checked = settings.gridEnabled,
            onCheckedChange = vm::setGridEnabled,
        )
    }
    item {
        SliderRow(
            label = "Размер ячейки сетки",
            value = settings.gridSize.toFloat(),
            valueRange = 10f..200f,
            valueLabel = { "${it.toInt()} px" },
            onValueChange = { vm.setGridSize(it.toInt()) },
        )
    }
    item {
        SwitchRow(
            label = "Привязка к сетке",
            description = "Границы элементов прилипают к линиям сетки при перемещении и изменении размера",
            checked = settings.snapToGrid,
            onCheckedChange = vm::setSnapToGrid,
        )
    }
    item { PanelSectionTitle("Привязка (Snap)") }
    item {
        SwitchRow(
            label = "Магнитная привязка",
            description = "Элементы примагничиваются к центру и краям холста, к границам других элементов",
            checked = settings.snapEnabled,
            onCheckedChange = vm::setSnapEnabled,
        )
    }
    item {
        SliderRow(
            label = "Чувствительность к холсту",
            value = settings.snapCanvasSensitivity.toFloat(),
            valueRange = 3f..50f,
            valueLabel = { "${it.toInt()} px" },
            onValueChange = { vm.setSnapCanvasSensitivity(it.toInt()) },
        )
    }
    item {
        SliderRow(
            label = "Чувствительность к границам элементов",
            value = settings.snapElementsSensitivity.toFloat(),
            valueRange = 2f..30f,
            valueLabel = { "${it.toInt()} px" },
            onValueChange = { vm.setSnapElementsSensitivity(it.toInt()) },
        )
    }
    item { PanelSectionTitle("Направляющие") }
    item {
        SwitchRow(
            label = "Линейки",
            description = "Шкала координат по краям холста",
            checked = settings.showRulers,
            onCheckedChange = vm::setShowRulers,
        )
    }
    item {
        SwitchRow(
            label = "Центральные направляющие",
            description = "Пунктирные линии по центру холста",
            checked = settings.showCenterGuides,
            onCheckedChange = vm::setShowCenterGuides,
        )
    }
    item { PanelSectionTitle("Фон холста") }
    item {
        SwitchRow(
            label = "Шахматный фон",
            description = "Показывает прозрачность как в графических редакторах",
            checked = settings.checkerboardBackground,
            onCheckedChange = vm::setCheckerboardBackground,
        )
    }
    item {
        SwitchRow(
            label = "Затемнять область вне холста",
            description = "Холст сильнее выделяется на фоне",
            checked = settings.canvasDimOutside,
            onCheckedChange = vm::setCanvasDimOutside,
        )
    }
}

// ==================================================================
// Раздел «Редактор»
// ==================================================================

private fun androidx.compose.foundation.lazy.LazyListScope.editorSection(
    settings: com.fallen.studio.data.AppSettings,
    vm: SettingsViewModel,
) {
    item { PanelSectionTitle("Автосохранение") }
    item {
        SwitchRow(
            label = "Автосохранение",
            description = "Каждое изменение автоматически сохраняет проект — он сразу появляется на главном экране",
            checked = settings.autosaveEnabled,
            onCheckedChange = vm::setAutosaveEnabled,
        )
    }
    item { PanelSectionTitle("Изображения") }
    item {
        SwitchRow(
            label = "Растягивание изображения",
            description = "Вкл: изображение растягивается под рамку. Выкл: рамка растёт, а изображение вписывается по центру без искажений (как текст)",
            checked = settings.imageStretchEnabled,
            onCheckedChange = vm::setImageStretchEnabled,
        )
    }
    item { PanelSectionTitle("История изменений") }
    item {
        SliderRow(
            label = "Глубина отката (undo)",
            value = settings.undoLimit.toFloat(),
            valueRange = 5f..200f,
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
            description = "Вибрация при выделении и привязке",
            checked = settings.hapticFeedback,
            onCheckedChange = vm::setHapticFeedback,
        )
    }
    item {
        SwitchRow(
            label = "Подсказка размеров",
            description = "Показывать размер элемента при изменении",
            checked = settings.showSizeTooltip,
            onCheckedChange = vm::setShowSizeTooltip,
        )
    }
    item {
        SwitchRow(
            label = "Сохранять пропорции",
            description = "По умолчанию при изменении размера",
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
// Раздел «Экспорт»
// ==================================================================

private fun androidx.compose.foundation.lazy.LazyListScope.exportSection(
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
            description = "Добавлять пояснения в экспортируемый код",
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
// Раздел «О приложении»
// ==================================================================

private fun androidx.compose.foundation.lazy.LazyListScope.aboutSection(
    onOpenTutorial: () -> Unit,
) {
    item {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
        }
    }
    item { PanelSectionTitle("С чего начать") }
    item {
        val colors = FallenTheme.colors
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.divider, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf(
                "1. Нажмите «Новый проект» на главном экране и выберите разрешение холста.",
                "2. Через панель «Ассеты» добавьте свои изображения — кнопки, иконки, рамки.",
                "3. Через панель «Текст» добавьте надписи: шрифт, обводка, тень настраиваются в свойствах.",
                "4. Перетаскивайте элементы пальцем. Точки по краям меняют размер, а точка в правом нижнем углу масштабирует пропорционально.",
                "5. Включите сетку и привязку в настройках, чтобы выравнивать элементы идеально ровно.",
                "6. Готовый макет экспортируйте в PNG или в код (Unity C#, JSON и другие) через панель «Экспорт».",
            ).forEach { step ->
                Text(
                    text = step,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
    item { PanelSectionTitle("Обучение") }
    item {
        val colors = FallenTheme.colors
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.divider, RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenTutorial)
                .padding(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Пройти обучение",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Пошаговое знакомство с редактором. Можно пропустить в любой момент",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
    item { PanelSectionTitle("Мы на связи") }
    item {
        val context = LocalContext.current
        // Круглые кнопки соцсетей без подписей
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        ) {
            SocialCircleButton(
                iconRes = R.drawable.ic_telegram,
                contentDescription = "Telegram — официальная группа Fallen",
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://t.me/Fallen_OfficialGroup"),
                    )
                    runCatching { context.startActivity(intent) }
                },
            )
            SocialCircleButton(
                iconRes = R.drawable.ic_vk,
                contentDescription = "VK — официальная группа Fallen",
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://m.vk.com/fallen_officialgroup"),
                    )
                    runCatching { context.startActivity(intent) }
                },
            )
        }
    }
}

/** Круглая кнопка соцсети: белый круг с PNG-иконкой, без текста */
@Composable
private fun SocialCircleButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = FallenTheme.colors
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(1.dp, colors.divider, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(28.dp),
        )
    }
}

// ==================================================================
// Обучение: пошаговый оверлей, можно пропустить в любой момент
// ==================================================================

private data class TutorialStep(val title: String, val body: String)

private val tutorialSteps = listOf(
    TutorialStep(
        "Добро пожаловать в Fallen",
        "Fallen помогает расставлять элементы интерфейса на холсте и переносить готовую расстановку в игру или приложение. Пройдём по основам за пару минут.",
    ),
    TutorialStep(
        "Создание проекта",
        "На главном экране нажмите «Новый проект» и выберите разрешение холста — базовое или своё. Проект сохраняется автоматически при каждом изменении.",
    ),
    TutorialStep(
        "Ассеты и текст",
        "Нижняя панель редактора: «Ассеты» — импорт ваших изображений, «Текст» — надписи со шрифтами, обводкой и тенью. Всё добавленное появляется на холсте.",
    ),
    TutorialStep(
        "Перемещение и размер",
        "Перетаскивайте элементы пальцем. Точки на рамке меняют размер, а отдельная точка в правом нижнем углу масштабирует элемент пропорционально — вместе со шрифтом текста.",
    ),
    TutorialStep(
        "Сетка и привязка",
        "В настройках холста включите сетку и привязку — элементы будут прилипать к линиям, а магнитная привязка поможет выравнивать по центру и краям.",
    ),
    TutorialStep(
        "Экспорт",
        "Панель «Экспорт» выгружает макет в PNG или в код: Unity C#, JSON, CSV, XML. Файл проекта .fallen можно сохранить и отправить другому пользователю.",
    ),
)

@Composable
private fun TutorialOverlay(onClose: () -> Unit) {
    val colors = FallenTheme.colors
    var step by remember { mutableIntStateOf(0) }
    val last = step == tutorialSteps.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.divider, RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Индикатор шагов
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tutorialSteps.forEachIndexed { i, _ ->
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (i <= step) MaterialTheme.colorScheme.primary
                                else colors.divider,
                            ),
                    )
                }
            }
            Text(
                text = tutorialSteps[step].title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = tutorialSteps[step].body,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = colors.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) {
                    Text("Пропустить")
                }
                Spacer(Modifier.weight(1f))
                if (step > 0) {
                    TextButton(onClick = { step-- }) {
                        Text("Назад")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Button(onClick = {
                    if (last) onClose() else step++
                }) {
                    Text(if (last) "Готово" else "Далее")
                }
            }
        }
    }
}
