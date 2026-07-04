package com.fallen.studio.export

import com.fallen.studio.data.model.CanvasElement
import com.fallen.studio.data.model.FallenProject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/** Форматы экспорта расстановки */
enum class ExportFormat(
    val id: String,
    val title: String,
    val description: String,
    val fileExtension: String,
) {
    UNITY("unity", "Unity C#", "Скрипт расстановки для Unity UI (RectTransform)", "cs"),
    JSON("json", "JSON", "Универсальный формат: имя, координаты, размер каждого элемента", "json"),
    TXT("txt", "Текст (TXT)", "Простой список: название ассета и его координаты", "txt"),
    CSV("csv", "CSV-таблица", "Таблица для Excel / Google Sheets", "csv"),
    XML("xml", "XML", "Структурированная разметка для любых движков", "xml"),
}

/**
 * Движок экспорта Fallen.
 * Генерирует расстановку элементов в разных форматах —
 * не только для Unity (пункт 9 требований).
 *
 * Система координат исходника: (0,0) — левый верхний угол холста,
 * x/y — левый верхний угол элемента.
 * Для Unity дополнительно считаем anchoredPosition относительно центра.
 */
object ExportEngine {

    private fun Float.fmt(): String = String.format(Locale.US, "%.1f", this)

    fun generate(project: FallenProject, format: ExportFormat, includeComments: Boolean): String =
        when (format) {
            ExportFormat.UNITY -> generateUnity(project, includeComments)
            ExportFormat.JSON -> generateJson(project)
            ExportFormat.TXT -> generateTxt(project, includeComments)
            ExportFormat.CSV -> generateCsv(project)
            ExportFormat.XML -> generateXml(project)
        }

    // ------------------------------------------------------------
    // Unity C#
    // ------------------------------------------------------------
    private fun generateUnity(project: FallenProject, includeComments: Boolean): String {
        val sb = StringBuilder()
        if (includeComments) {
            sb.appendLine("// ============================================")
            sb.appendLine("// Сгенерировано в Fallen")
            sb.appendLine("// Проект: ${project.name}")
            sb.appendLine("// Холст: ${project.canvas.w} x ${project.canvas.h}")
            sb.appendLine("// ============================================")
        }
        sb.appendLine("using UnityEngine;")
        sb.appendLine("using UnityEngine.UI;")
        sb.appendLine()
        sb.appendLine("public class FallenLayout : MonoBehaviour")
        sb.appendLine("{")
        sb.appendLine("    [Header(\"Перетащите сюда RectTransform элементов\")]")

        val sorted = project.elements.sortedBy { it.z }
        sorted.forEach { el ->
            sb.appendLine("    public RectTransform ${el.safeVarName()};")
        }
        sb.appendLine()
        sb.appendLine("    void Start()")
        sb.appendLine("    {")
        sb.appendLine("        ApplyLayout();")
        sb.appendLine("    }")
        sb.appendLine()
        sb.appendLine("    public void ApplyLayout()")
        sb.appendLine("    {")

        val cw = project.canvas.w.toFloat()
        val ch = project.canvas.h.toFloat()
        sorted.forEach { el ->
            // Центр элемента относительно центра холста, ось Y инвертирована (Unity: вверх +)
            val cx = el.x + el.w / 2f - cw / 2f
            val cy = -(el.y + el.h / 2f - ch / 2f)
            if (includeComments) {
                sb.appendLine("        // ${el.displayName()} (${if (el.isText) "текст" else "изображение"})")
            }
            val v = el.safeVarName()
            sb.appendLine("        if ($v != null)")
            sb.appendLine("        {")
            sb.appendLine("            $v.anchorMin = new Vector2(0.5f, 0.5f);")
            sb.appendLine("            $v.anchorMax = new Vector2(0.5f, 0.5f);")
            sb.appendLine("            $v.anchoredPosition = new Vector2(${cx.fmt()}f, ${cy.fmt()}f);")
            sb.appendLine("            $v.sizeDelta = new Vector2(${el.w.fmt()}f, ${el.h.fmt()}f);")
            if (el.rotation != 0f) {
                sb.appendLine("            $v.localEulerAngles = new Vector3(0f, 0f, ${(-el.rotation).fmt()}f);")
            }
            sb.appendLine("        }")
        }
        sb.appendLine("    }")
        sb.appendLine("}")
        return sb.toString()
    }

    // ------------------------------------------------------------
    // JSON
    // ------------------------------------------------------------
    private fun generateJson(project: FallenProject): String {
        val json = Json { prettyPrint = true }
        val elements = project.elements.sortedBy { it.z }.map { el ->
            JsonObject(
                buildMap {
                    put("name", JsonPrimitive(el.displayName()))
                    put("type", JsonPrimitive(el.type))
                    put("x", JsonPrimitive(el.x))
                    put("y", JsonPrimitive(el.y))
                    put("width", JsonPrimitive(el.w))
                    put("height", JsonPrimitive(el.h))
                    put("centerX", JsonPrimitive(el.x + el.w / 2f))
                    put("centerY", JsonPrimitive(el.y + el.h / 2f))
                    put("layer", JsonPrimitive(el.z))
                    if (el.rotation != 0f) put("rotation", JsonPrimitive(el.rotation))
                    if (el.opacity != 100f) put("opacity", JsonPrimitive(el.opacity))
                    if (el.isText) put("text", JsonPrimitive(el.text ?: ""))
                }
            )
        }
        val root = JsonObject(
            mapOf(
                "project" to JsonPrimitive(project.name),
                "canvas" to JsonObject(
                    mapOf(
                        "width" to JsonPrimitive(project.canvas.w),
                        "height" to JsonPrimitive(project.canvas.h),
                    )
                ),
                "elements" to JsonArray(elements),
            )
        )
        return json.encodeToString(root)
    }

    // ------------------------------------------------------------
    // TXT — простой человекочитаемый список
    // ------------------------------------------------------------
    private fun generateTxt(project: FallenProject, includeComments: Boolean): String {
        val sb = StringBuilder()
        if (includeComments) {
            sb.appendLine("Расстановка элементов — ${project.name}")
            sb.appendLine("Холст: ${project.canvas.w} x ${project.canvas.h}")
            sb.appendLine("Координаты: левый верхний угол элемента (x, y)")
            sb.appendLine("=".repeat(48))
        }
        project.elements.sortedBy { it.z }.forEach { el ->
            sb.appendLine(
                "${el.displayName()}: x=${el.x.fmt()}, y=${el.y.fmt()}, " +
                    "ширина=${el.w.fmt()}, высота=${el.h.fmt()}"
            )
        }
        return sb.toString()
    }

    // ------------------------------------------------------------
    // CSV
    // ------------------------------------------------------------
    private fun generateCsv(project: FallenProject): String {
        val sb = StringBuilder()
        sb.appendLine("name,type,x,y,width,height,centerX,centerY,layer,rotation,opacity")
        project.elements.sortedBy { it.z }.forEach { el ->
            val name = el.displayName().replace("\"", "\"\"")
            sb.appendLine(
                "\"$name\",${el.type},${el.x.fmt()},${el.y.fmt()},${el.w.fmt()},${el.h.fmt()}," +
                    "${(el.x + el.w / 2f).fmt()},${(el.y + el.h / 2f).fmt()}," +
                    "${el.z},${el.rotation.fmt()},${el.opacity.fmt()}"
            )
        }
        return sb.toString()
    }

    // ------------------------------------------------------------
    // XML
    // ------------------------------------------------------------
    private fun generateXml(project: FallenProject): String {
        fun esc(s: String) = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<layout project=\"${esc(project.name)}\" canvasWidth=\"${project.canvas.w}\" canvasHeight=\"${project.canvas.h}\">")
        project.elements.sortedBy { it.z }.forEach { el ->
            sb.append("  <element")
            sb.append(" name=\"${esc(el.displayName())}\"")
            sb.append(" type=\"${el.type}\"")
            sb.append(" x=\"${el.x.fmt()}\"")
            sb.append(" y=\"${el.y.fmt()}\"")
            sb.append(" width=\"${el.w.fmt()}\"")
            sb.append(" height=\"${el.h.fmt()}\"")
            sb.append(" layer=\"${el.z}\"")
            if (el.rotation != 0f) sb.append(" rotation=\"${el.rotation.fmt()}\"")
            if (el.isText) {
                sb.appendLine(">")
                sb.appendLine("    <text>${esc(el.text ?: "")}</text>")
                sb.appendLine("  </element>")
            } else {
                sb.appendLine(" />")
            }
        }
        sb.appendLine("</layout>")
        return sb.toString()
    }

    // ------------------------------------------------------------
    // Вспомогательные
    // ------------------------------------------------------------
    private fun CanvasElement.displayName(): String =
        name.ifBlank { if (isText) (text ?: "Text").take(24) else "Element" }

    private fun CanvasElement.safeVarName(): String {
        val base = displayName()
            .replace(Regex("[^A-Za-z0-9_]"), "_")
            .trim('_')
            .ifBlank { "element" }
        val prefixed = if (base.first().isDigit()) "el_$base" else base
        return prefixed.replaceFirstChar { it.lowercaseChar() }
    }
}
