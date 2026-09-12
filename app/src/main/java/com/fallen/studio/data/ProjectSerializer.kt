package com.fallen.studio.data

import com.fallen.studio.data.model.FallenProject
import com.fallen.studio.data.model.ProjectGraph
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Читает поддерживаемую схему legacy v1..3 и v4, пишет только canonical v4.
 * Неизвестные поля поддерживаемых версий игнорируются (не сохраняются как DOM).
 * Будущая версия, ошибочные типы и битые ссылки отклоняются, а не исправляются.
 * Это не обещание совместимости с произвольным вариантом .uiproj.
 */
@OptIn(ExperimentalSerializationApi::class)
object ProjectSerializer {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        encodeDefaults = true
        coerceInputValues = false
        explicitNulls = false
    }

    /** Невалидный/future snapshot вызывает исключение до записи файла. */
    fun encode(project: FallenProject): String =
        json.encodeToString(FallenProject.serializer(), ProjectGraph.normalize(project))

    /** Отделяет все списки до нормализации и переключения repository на IO. */
    internal fun canonicalSnapshot(project: FallenProject): FallenProject = ProjectGraph.normalize(
        project.copy(
            assets = project.assets.toList(),
            elements = project.elements.toList(),
            fonts = project.fonts.toList(),
            nodes = project.nodes.map { it.copy(elements = it.elements.toList()) },
            transitions = project.transitions.toList(),
        ),
    )

    /**
     * API импорта: нормализованный v4 либо null, в том числе для будущей версии.
     * Отсутствующая version допустима лишь для legacy с canvas или elements;
     * входной v4 обязан содержать непустой nodes, даже если у него есть mirror.
     */
    fun decode(raw: String): FallenProject? = try {
        decodeForStorage(raw)
    } catch (_: UnsupportedProjectVersionException) {
        null
    }

    /**
     * null — повреждённый документ; неподдерживаемая version — не corruption.
     * Хранилище обязано прервать fallback/запись, не заменять её старым backup.
     */
    internal fun decodeForStorage(raw: String): FallenProject? = try {
        val root = json.parseToJsonElement(raw) as? JsonObject
        if (root == null) null else decodeProject(root)
    } catch (error: UnsupportedProjectVersionException) {
        throw error
    } catch (_: Exception) {
        null
    }

    /** Валидатор FileStore: false для corruption, исключение для unsupported version. */
    internal fun isValidForStorage(raw: String): Boolean = decodeForStorage(raw) != null

    private fun decodeProject(root: JsonObject): FallenProject? {
        val rawVersion = root["version"]
        val version = if (rawVersion == null) {
            3 // До появления nodes отсутствие version означало legacy-проект.
        } else {
            val value = rawVersion as? JsonPrimitive ?: return null
            if (value.isString) return null
            value.intOrNull ?: return null
        }
        if (version !in 1..ProjectGraph.VERSION) throw UnsupportedProjectVersionException(version)
        if (version == ProjectGraph.VERSION) {
            val nodes = root["nodes"] as? JsonArray ?: return null
            if (nodes.isEmpty()) return null
        } else if ("canvas" !in root && "elements" !in root) {
            return null
        }

        val serializer = FallenProject.serializer()
        if (!hasValidTypes(root, serializer.descriptor)) return null
        // Model default = 4, но у versionless legacy должен сохраниться путь миграции.
        val document = if (rawVersion == null) {
            JsonObject(root + ("version" to JsonPrimitive(version)))
        } else {
            root
        }
        return ProjectGraph.normalize(json.decodeFromJsonElement(serializer, document))
    }

    /**
     * Json tree decoder допускает некоторые числа/boolean из строк. Проверяем
     * именно JSON-типы известных полей; defaults/required/enum проверит serializer.
     * Неизвестные поля не обходим и не строим слой произвольного DOM.
     */
    private fun hasValidTypes(value: JsonElement, descriptor: SerialDescriptor): Boolean {
        if (value == JsonNull) return descriptor.isNullable
        return when (descriptor.kind) {
            PrimitiveKind.STRING, PrimitiveKind.CHAR, SerialKind.ENUM ->
                value is JsonPrimitive && value.isString
            PrimitiveKind.BOOLEAN ->
                value is JsonPrimitive && !value.isString && value.booleanOrNull != null
            PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG,
            PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE ->
                value is JsonPrimitive && !value.isString && value.doubleOrNull != null
            StructureKind.CLASS, StructureKind.OBJECT -> value is JsonObject &&
                (0 until descriptor.elementsCount).all { index ->
                    val field = value[descriptor.getElementName(index)]
                    field == null || hasValidTypes(field, descriptor.getElementDescriptor(index))
                }
            StructureKind.LIST -> value is JsonArray && value.all {
                hasValidTypes(it, descriptor.getElementDescriptor(0))
            }
            else -> false
        }
    }
}

/** Не IOException: FileStore не должен принимать несовместимость схемы за ошибку чтения. */
internal class UnsupportedProjectVersionException(val version: Int) :
    IllegalArgumentException("Unsupported project version: $version (supported: 1..${ProjectGraph.VERSION})")
