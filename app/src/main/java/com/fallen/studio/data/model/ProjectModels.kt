package com.fallen.studio.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================
// Модели данных Fallen.
// Формат на 100% совместим с .uiproj из UI Studio Pro (HTML):
//   { version, name, created, canvas:{w,h}, assets:[], elements:[], fonts:[], counter }
// ============================================================

/** Размер холста */
@Serializable
data class CanvasSize(
    val w: Int = 2340,
    val h: Int = 1080,
)

/** Ассет — изображение, загруженное пользователем (src = data URL base64) */
@Serializable
data class Asset(
    val id: String,
    val name: String,
    val src: String,
)

/** Пользовательский шрифт (src = data URL base64) */
@Serializable
data class ProjectFont(
    val id: String,
    val name: String,
    val src: String,
    val format: String = "truetype",
    val fileName: String = "",
)

/**
 * Элемент на холсте. Единый класс для текста и изображений —
 * так же, как в исходном JSON-формате (поле type различает их).
 */
@Serializable
data class CanvasElement(
    val id: String,
    val type: String,                    // "text" | "image"
    val name: String = "",
    // Геометрия
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 100f,
    val h: Float = 100f,
    val z: Int = 0,
    val locked: Boolean = false,
    val opacity: Float = 100f,
    val rotation: Float = 0f,
    // Изображение
    val assetId: String? = null,
    val src: String? = null,
    // Текст
    val text: String? = null,
    val fontFamily: String? = null,
    val fontSize: Int? = null,
    val fontWeight: String? = null,      // "400" | "700" | "bold" и т.п.
    val color: String? = null,           // hex "#RRGGBB"
    val textAlign: String? = null,       // "left" | "center" | "right"
    val strokeWidth: Float? = null,
    val strokeColor: String? = null,
    val lineHeight: Float? = null,       // 0..100 (слайдер из старой версии)
    val letterSpacing: Float? = null,    // 0..100
    // Тень текста
    val shadowEnabled: Boolean? = null,
    val shadowBlur: Float? = null,
    val shadowOpacity: Float? = null,    // 0..100
    val shadowX: Float? = null,          // 0..100, центр = 50
    val shadowY: Float? = null,
    val shadowColor: String? = null,
) {
    val isText: Boolean get() = type == "text"
    val isImage: Boolean get() = type == "image"
}

/** Проект целиком — корневой объект .uiproj / .fallen файла */
@Serializable
data class FallenProject(
    val version: Int = 3,
    val id: String? = null,
    val name: String = "Untitled",
    val created: String = "",
    val modified: String? = null,
    val canvas: CanvasSize = CanvasSize(),
    val assets: List<Asset> = emptyList(),
    val elements: List<CanvasElement> = emptyList(),
    val fonts: List<ProjectFont> = emptyList(),
    val counter: Int = 0,
)

/** Краткая информация о проекте для списка на главном экране */
data class ProjectSummary(
    val id: String,
    val name: String,
    val created: String,
    val modified: String?,
    val elementCount: Int,
    val textCount: Int,
    val imageCount: Int,
    val canvasW: Int,
    val canvasH: Int,
    /** src первого image-ассета для миниатюры (data URL) */
    val thumbnailSrc: String?,
)

fun FallenProject.toSummary(): ProjectSummary {
    val texts = elements.count { it.isText }
    return ProjectSummary(
        id = id ?: "",
        name = name,
        created = created,
        modified = modified,
        elementCount = elements.size,
        textCount = texts,
        imageCount = elements.size - texts,
        canvasW = canvas.w,
        canvasH = canvas.h,
        thumbnailSrc = assets.firstOrNull()?.src,
    )
}
