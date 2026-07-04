package com.fallen.studio.data

import android.content.Context
import android.net.Uri
import com.fallen.studio.data.model.FallenProject
import com.fallen.studio.data.model.ProjectSummary
import com.fallen.studio.data.model.toSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * Хранилище проектов: каждый проект — отдельный JSON-файл
 * в internal storage (filesDir/projects/<id>.fallen).
 * Это надёжнее и быстрее, чем один общий файл.
 */
class ProjectRepository(private val context: Context) {

    private val projectsDir: File
        get() = File(context.filesDir, "projects").apply { mkdirs() }

    private val autosaveFile: File
        get() = File(context.filesDir, "autosave.fallen")

    private val _summaries = MutableStateFlow<List<ProjectSummary>>(emptyList())
    val summaries: StateFlow<List<ProjectSummary>> = _summaries.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val list = projectsDir.listFiles { f -> f.extension == "fallen" }
            ?.mapNotNull { file ->
                ProjectSerializer.decode(file.readText())
                    ?.copy(id = file.nameWithoutExtension)
                    ?.toSummary()
            }
            ?.sortedByDescending { it.modified ?: it.created }
            ?: emptyList()
        _summaries.value = list
    }

    suspend fun load(id: String): FallenProject? = withContext(Dispatchers.IO) {
        val file = File(projectsDir, "$id.fallen")
        if (!file.exists()) return@withContext null
        ProjectSerializer.decode(file.readText())?.copy(id = id)
    }

    /** Сохраняет проект; если id пустой — создаёт новый. Возвращает id. */
    suspend fun save(project: FallenProject): String = withContext(Dispatchers.IO) {
        val id = project.id?.takeIf { it.isNotBlank() }
            ?: "proj_${System.currentTimeMillis()}"
        val toSave = project.copy(
            id = id,
            created = project.created.ifBlank { Instant.now().toString() },
            modified = Instant.now().toString(),
        )
        File(projectsDir, "$id.fallen").writeText(ProjectSerializer.encode(toSave))
        refresh()
        id
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(projectsDir, "$id.fallen").delete()
        refresh()
    }

    suspend fun duplicate(id: String): String? = withContext(Dispatchers.IO) {
        val original = load(id) ?: return@withContext null
        save(original.copy(id = null, name = original.name + " (копия)", created = ""))
    }

    suspend fun rename(id: String, newName: String) = withContext(Dispatchers.IO) {
        val project = load(id) ?: return@withContext
        save(project.copy(name = newName))
    }

    // ---------- Импорт / экспорт файлов ----------

    /** Импорт .uiproj / .fallen / .json файла из старой HTML-программы или экспорта Fallen. */
    suspend fun importFromUri(uri: Uri): FallenProject? = withContext(Dispatchers.IO) {
        try {
            val raw = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return@withContext null
            ProjectSerializer.decode(raw)
        } catch (_: Exception) {
            null
        }
    }

    /** Экспорт проекта в выбранный пользователем файл (SAF). */
    suspend fun exportToUri(project: FallenProject, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(ProjectSerializer.encode(project).toByteArray())
                }
                true
            } catch (_: Exception) {
                false
            }
        }

    /** Запись произвольного текста (код расстановки и т.п.) в файл через SAF. */
    suspend fun writeTextToUri(text: String, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray())
                }
                true
            } catch (_: Exception) {
                false
            }
        }

    /** Запись бинарных данных (PNG/JPEG) в файл через SAF. */
    suspend fun writeBytesToUri(bytes: ByteArray, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                }
                true
            } catch (_: Exception) {
                false
            }
        }

    // ---------- Автосохранение ----------

    suspend fun saveAutosave(project: FallenProject) = withContext(Dispatchers.IO) {
        try {
            autosaveFile.writeText(ProjectSerializer.encode(project))
        } catch (_: Exception) {
        }
    }

    suspend fun loadAutosave(): FallenProject? = withContext(Dispatchers.IO) {
        if (!autosaveFile.exists()) return@withContext null
        ProjectSerializer.decode(autosaveFile.readText())
    }

    suspend fun clearAutosave() = withContext(Dispatchers.IO) {
        autosaveFile.delete()
    }
}
