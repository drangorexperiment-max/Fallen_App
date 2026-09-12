package com.fallen.studio.ui.screens.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fallen.studio.data.ProjectRepository
import com.fallen.studio.data.model.ProjectSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeState(
    val projects: List<ProjectSummary> = emptyList(),
    val isLoading: Boolean = true,
    val toast: String? = null,
    /** id проекта, созданного импортом — сигнал открыть редактор */
    val openProjectId: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository(application)

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Ошибка хранилища не должна завершать Home или заменять прежний список пустым. */
    private fun launchOperation(errorMessage: String, operation: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                operation()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.value = _state.value.copy(isLoading = false, toast = errorMessage)
            }
        }
    }

    fun refresh() {
        _state.value = _state.value.copy(isLoading = true)
        launchOperation("Не удалось обновить список проектов. Попробуйте ещё раз.") {
            repository.refresh()
            _state.value = _state.value.copy(
                projects = repository.summaries.value,
                isLoading = false,
            )
        }
    }

    fun deleteProject(id: String) {
        launchOperation("Не удалось удалить проект. Проверьте доступ к хранилищу.") {
            repository.delete(id)
            _state.value = _state.value.copy(
                projects = repository.summaries.value,
                toast = "Проект удалён",
            )
        }
    }

    fun duplicateProject(id: String) {
        launchOperation("Не удалось создать копию проекта. Исходный проект не изменён.") {
            val idOfCopy = repository.duplicate(id)
            _state.value = _state.value.copy(
                projects = repository.summaries.value,
                toast = if (idOfCopy == null) "Проект не найден или не может быть прочитан" else "Проект дублирован",
            )
        }
    }

    fun renameProject(id: String, newName: String) {
        launchOperation("Не удалось переименовать проект. Попробуйте ещё раз.") {
            repository.rename(id, newName.ifBlank { "Мой проект" })
            _state.value = _state.value.copy(projects = repository.summaries.value)
        }
    }

    /** Экспорт полного проекта, который можно импортировать на другом устройстве. */
    fun exportProject(id: String, uri: Uri) {
        launchOperation("Не удалось экспортировать проект. Исходный проект не изменён.") {
            val project = repository.load(id)
            if (project == null) {
                _state.value = _state.value.copy(toast = "Проект не найден")
                return@launchOperation
            }
            val ok = repository.exportToUri(project, uri)
            _state.value = _state.value.copy(
                toast = if (ok) "Проект «${project.name}» экспортирован" else "Ошибка экспорта",
            )
        }
    }

    /** Импорт открывается только после успешного сохранения отдельной копии. */
    fun importProject(uri: Uri) {
        launchOperation("Не удалось сохранить импортированный проект. Проверьте свободное место.") {
            val project = repository.importFromUri(uri)
            if (project == null) {
                _state.value = _state.value.copy(
                    toast = "Не удалось прочитать файл. Поддерживаются .fallen, .uiproj и .json",
                )
                return@launchOperation
            }
            val id = repository.save(project.copy(id = null))
            _state.value = _state.value.copy(
                projects = repository.summaries.value,
                toast = "Проект «${project.name}» импортирован",
                openProjectId = id,
            )
        }
    }

    fun consumeOpenProject() {
        _state.value = _state.value.copy(openProjectId = null)
    }

    fun consumeToast() {
        _state.value = _state.value.copy(toast = null)
    }
}
