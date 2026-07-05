package com.fallen.studio.ui.screens.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fallen.studio.data.ProjectRepository
import com.fallen.studio.data.model.ProjectSummary
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

    fun refresh() {
        viewModelScope.launch {
            repository.refresh()
            _state.value = _state.value.copy(
                projects = repository.summaries.value,
                isLoading = false,
            )
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            _state.value = _state.value.copy(
                projects = repository.summaries.value,
                toast = "Проект удалён",
            )
        }
    }

    fun duplicateProject(id: String) {
        viewModelScope.launch {
            repository.duplicate(id)
            _state.value = _state.value.copy(
                projects = repository.summaries.value,
                toast = "Проект дублирован",
            )
        }
    }

    fun renameProject(id: String, newName: String) {
        viewModelScope.launch {
            repository.rename(id, newName.ifBlank { "Мой проект" })
            _state.value = _state.value.copy(projects = repository.summaries.value)
        }
    }

    /**
     * Экспорт проекта в файл (.fallen), чтобы передать другому
     * человеку — он сможет импортировать его у себя.
     */
    fun exportProject(id: String, uri: Uri) {
        viewModelScope.launch {
            val project = repository.load(id)
            if (project == null) {
                _state.value = _state.value.copy(toast = "Проект не найден")
                return@launch
            }
            val ok = repository.exportToUri(project, uri)
            _state.value = _state.value.copy(
                toast = if (ok) "Проект «${project.name}» экспортирован" else "Ошибка экспорта",
            )
        }
    }

    /**
     * Импорт файла проекта (.fallen, старого .uiproj или .json).
     * Проект сохраняется и сразу открывается.
     */
    fun importProject(uri: Uri) {
        viewModelScope.launch {
            val project = repository.importFromUri(uri)
            if (project == null) {
                _state.value = _state.value.copy(
                    toast = "Не удалось прочитать файл. Поддерживаются .fallen, .uiproj и .json",
                )
                return@launch
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
