package com.fallen.studio.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "fallen_settings")

/** Режим темы */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Все настройки приложения Fallen */
data class AppSettings(
    // Внешний вид
    val themeMode: ThemeMode = ThemeMode.DARK,
    // Канвас
    val gridEnabled: Boolean = true,
    val gridSize: Int = 50,
    val snapToGrid: Boolean = false,
    val snapEnabled: Boolean = true,
    val snapCanvasSensitivity: Int = 12,
    val snapElementsSensitivity: Int = 5,
    val showRulers: Boolean = true,
    val showCenterGuides: Boolean = true,
    val canvasDimOutside: Boolean = true,
    // Редактор
    val autosaveEnabled: Boolean = true,
    val autosaveIntervalSec: Int = 30,
    val undoLimit: Int = 50,
    val defaultCanvasW: Int = 2340,
    val defaultCanvasH: Int = 1080,
    val hapticFeedback: Boolean = true,
    val showSizeTooltip: Boolean = true,
    val keepAspectDefault: Boolean = true,
    /** Растягивание изображений при ресайзе рамки. Если выключено —
     *  рамка растёт, а изображение вписывается по центру без искажений (как текст). */
    val imageStretchEnabled: Boolean = true,
    // Экспорт
    val defaultExportFormat: String = "unity",
    val exportImageScale: Float = 1f,
    val includeComments: Boolean = true,
    /** Рисовать размерные метки внутри холста при сохранении как изображение */
    val exportDimensionLabels: Boolean = false,
    /** 8 пользовательских ячеек палитры (hex или пустая строка) */
    val customColors: List<String> = List(8) { "" },
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val gridEnabled = booleanPreferencesKey("grid_enabled")
        val gridSize = intPreferencesKey("grid_size")
        val snapToGrid = booleanPreferencesKey("snap_to_grid")
        val snapEnabled = booleanPreferencesKey("snap_enabled")
        val snapCanvasSensitivity = intPreferencesKey("snap_canvas_sensitivity")
        val snapElementsSensitivity = intPreferencesKey("snap_elements_sensitivity")
        val showRulers = booleanPreferencesKey("show_rulers")
        val showCenterGuides = booleanPreferencesKey("show_center_guides")
        val canvasDimOutside = booleanPreferencesKey("canvas_dim_outside")
        val autosaveEnabled = booleanPreferencesKey("autosave_enabled")
        val autosaveInterval = intPreferencesKey("autosave_interval")
        val undoLimit = intPreferencesKey("undo_limit")
        val defaultCanvasW = intPreferencesKey("default_canvas_w")
        val defaultCanvasH = intPreferencesKey("default_canvas_h")
        val hapticFeedback = booleanPreferencesKey("haptic_feedback")
        val showSizeTooltip = booleanPreferencesKey("show_size_tooltip")
        val keepAspectDefault = booleanPreferencesKey("keep_aspect_default")
        val imageStretchEnabled = booleanPreferencesKey("image_stretch_enabled")
        val defaultExportFormat = stringPreferencesKey("default_export_format")
        val exportImageScale = floatPreferencesKey("export_image_scale")
        val includeComments = booleanPreferencesKey("include_comments")
        val exportDimensionLabels = booleanPreferencesKey("export_dimension_labels")
        val customColors = stringPreferencesKey("custom_colors")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = runCatching {
                ThemeMode.valueOf(p[Keys.themeMode] ?: ThemeMode.DARK.name)
            }.getOrDefault(ThemeMode.DARK),
            gridEnabled = p[Keys.gridEnabled] ?: true,
            gridSize = p[Keys.gridSize] ?: 50,
            snapToGrid = p[Keys.snapToGrid] ?: false,
            snapEnabled = p[Keys.snapEnabled] ?: true,
            snapCanvasSensitivity = p[Keys.snapCanvasSensitivity] ?: 12,
            snapElementsSensitivity = p[Keys.snapElementsSensitivity] ?: 5,
            showRulers = p[Keys.showRulers] ?: true,
            showCenterGuides = p[Keys.showCenterGuides] ?: true,
            canvasDimOutside = p[Keys.canvasDimOutside] ?: true,
            autosaveEnabled = p[Keys.autosaveEnabled] ?: true,
            autosaveIntervalSec = p[Keys.autosaveInterval] ?: 30,
            undoLimit = p[Keys.undoLimit] ?: 50,
            defaultCanvasW = p[Keys.defaultCanvasW] ?: 2340,
            defaultCanvasH = p[Keys.defaultCanvasH] ?: 1080,
            hapticFeedback = p[Keys.hapticFeedback] ?: true,
            showSizeTooltip = p[Keys.showSizeTooltip] ?: true,
            keepAspectDefault = p[Keys.keepAspectDefault] ?: true,
            imageStretchEnabled = p[Keys.imageStretchEnabled] ?: true,
            defaultExportFormat = p[Keys.defaultExportFormat] ?: "unity",
            exportImageScale = p[Keys.exportImageScale] ?: 1f,
            includeComments = p[Keys.includeComments] ?: true,
            exportDimensionLabels = p[Keys.exportDimensionLabels] ?: false,
            customColors = (p[Keys.customColors] ?: "")
                .split("|")
                .let { list -> List(8) { i -> list.getOrNull(i) ?: "" } },
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.dataStore.edit { it[Keys.themeMode] = mode.name }

    suspend fun setGridEnabled(v: Boolean) =
        context.dataStore.edit { it[Keys.gridEnabled] = v }

    suspend fun setGridSize(v: Int) =
        context.dataStore.edit { it[Keys.gridSize] = v }

    suspend fun setSnapToGrid(v: Boolean) =
        context.dataStore.edit { it[Keys.snapToGrid] = v }

    suspend fun setSnapEnabled(v: Boolean) =
        context.dataStore.edit { it[Keys.snapEnabled] = v }

    suspend fun setSnapCanvasSensitivity(v: Int) =
        context.dataStore.edit { it[Keys.snapCanvasSensitivity] = v }

    suspend fun setSnapElementsSensitivity(v: Int) =
        context.dataStore.edit { it[Keys.snapElementsSensitivity] = v }

    suspend fun setShowRulers(v: Boolean) =
        context.dataStore.edit { it[Keys.showRulers] = v }

    suspend fun setShowCenterGuides(v: Boolean) =
        context.dataStore.edit { it[Keys.showCenterGuides] = v }

    suspend fun setCanvasDimOutside(v: Boolean) =
        context.dataStore.edit { it[Keys.canvasDimOutside] = v }

    suspend fun setAutosaveEnabled(v: Boolean) =
        context.dataStore.edit { it[Keys.autosaveEnabled] = v }

    suspend fun setAutosaveInterval(v: Int) =
        context.dataStore.edit { it[Keys.autosaveInterval] = v }

    suspend fun setUndoLimit(v: Int) =
        context.dataStore.edit { it[Keys.undoLimit] = v }

    suspend fun setDefaultCanvas(w: Int, h: Int) =
        context.dataStore.edit {
            it[Keys.defaultCanvasW] = w
            it[Keys.defaultCanvasH] = h
        }

    suspend fun setHapticFeedback(v: Boolean) =
        context.dataStore.edit { it[Keys.hapticFeedback] = v }

    suspend fun setShowSizeTooltip(v: Boolean) =
        context.dataStore.edit { it[Keys.showSizeTooltip] = v }

    suspend fun setKeepAspectDefault(v: Boolean) =
        context.dataStore.edit { it[Keys.keepAspectDefault] = v }

    suspend fun setImageStretchEnabled(v: Boolean) =
        context.dataStore.edit { it[Keys.imageStretchEnabled] = v }

    suspend fun setDefaultExportFormat(v: String) =
        context.dataStore.edit { it[Keys.defaultExportFormat] = v }

    suspend fun setExportImageScale(v: Float) =
        context.dataStore.edit { it[Keys.exportImageScale] = v }

    suspend fun setIncludeComments(v: Boolean) =
        context.dataStore.edit { it[Keys.includeComments] = v }

    suspend fun setExportDimensionLabels(v: Boolean) =
        context.dataStore.edit { it[Keys.exportDimensionLabels] = v }

    /** Сохраняет цвет в одну из 8 пользовательских ячеек палитры */
    suspend fun setCustomColor(index: Int, hex: String) =
        context.dataStore.edit { p ->
            val current = (p[Keys.customColors] ?: "")
                .split("|")
                .let { list -> MutableList(8) { i -> list.getOrNull(i) ?: "" } }
            if (index in 0..7) current[index] = hex
            p[Keys.customColors] = current.joinToString("|")
        }
}
