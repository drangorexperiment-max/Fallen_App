package com.fallen.studio.data

import com.fallen.studio.data.model.FallenProject
import kotlinx.serialization.json.Json

/**
 * Сериализация/десериализация проектов.
 * ignoreUnknownKeys + isLenient — чтобы любые старые .uiproj файлы
 * (в том числе с лишними/недостающими полями) открывались без ошибок.
 */
object ProjectSerializer {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun encode(project: FallenProject): String =
        json.encodeToString(FallenProject.serializer(), project)

    /**
     * Читает проект из JSON-строки (.uiproj / .fallen).
     * @return null, если файл не является проектом.
     */
    fun decode(raw: String): FallenProject? = try {
        val project = json.decodeFromString(FallenProject.serializer(), raw)
        // Минимальная валидация как в оригинале: canvas + assets + elements
        if (project.canvas.w <= 0 || project.canvas.h <= 0) null else project
    } catch (_: Exception) {
        null
    }
}
