package com.fallen.studio.ui.screens.editor.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fallen.studio.data.model.CanvasElement
import com.fallen.studio.data.model.NodeTransition
import com.fallen.studio.data.model.ScreenNode
import com.fallen.studio.data.model.TransitionKind
import com.fallen.studio.ui.screens.editor.EditorState
import com.fallen.studio.ui.screens.editor.NodesWorkspace
import com.fallen.studio.ui.theme.FallenTheme

/** Full workspace, not a fixed-height sheet: it can scroll even in phone landscape. */
@Composable
fun NodesPanel(
    state: EditorState,
    onOpenNode: (String) -> Unit,
    onReturnToCanvas: () -> Unit,
    onCreateNode: (String, Int, Int) -> Unit,
    onRenameNode: (String, String) -> Unit,
    onDuplicateNode: (String) -> Unit,
    onDeleteNode: (String) -> Unit,
    onSetStartNode: (String) -> Unit,
    onSetElementTransition: (String, String) -> Unit,
    onSetElementBackAction: (String) -> Unit,
    onClearElementTransition: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The active authoring canvas is authoritative even while a gesture is in progress.
    val nodes = state.nodes.map { node ->
        if (node.id == state.activeNodeId) node.copy(canvas = state.canvas, elements = state.elements)
        else node
    }
    var graphMode by rememberSaveable(state.sessionId) { mutableStateOf(true) }
    var showCreate by rememberSaveable(state.sessionId) { mutableStateOf(false) }
    var renameId by rememberSaveable(state.sessionId) { mutableStateOf<String?>(null) }
    var duplicateId by rememberSaveable(state.sessionId) { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable(state.sessionId) { mutableStateOf<String?>(null) }

    val nodeContent: @Composable (ScreenNode, Modifier) -> Unit = { node, cardModifier ->
        NodeCard(
            node = node,
            nodes = nodes,
            outgoing = state.transitions.filter { it.fromNodeId == node.id },
            isActive = node.id == state.activeNodeId,
            isStart = node.id == state.startNodeId,
            canDelete = nodes.size > 1,
            onOpen = { onOpenNode(node.id) },
            onRename = { renameId = node.id },
            onDuplicate = { duplicateId = node.id },
            onDelete = { deleteId = node.id },
            onSetStart = { onSetStartNode(node.id) },
            modifier = cardModifier,
        )
    }
    val actionEditor: @Composable () -> Unit = {
        ElementTransitionEditor(
            element = state.selectedElement,
            nodes = nodes,
            transition = state.transitions.lastOrNull {
                it.fromNodeId == state.activeNodeId && it.elementId == state.selectedId
            },
            onSetTransition = onSetElementTransition,
            onSetBack = onSetElementBackAction,
            onClear = onClearElementTransition,
        )
    }
    val workspace: @Composable () -> Unit = {
        NodesHeader(
            nodeCount = nodes.size,
            transitionCount = state.transitions.size,
            graphMode = graphMode,
            onGraphMode = { graphMode = it },
            onCreate = { showCreate = true },
            onReturnToCanvas = onReturnToCanvas,
        )
        if (nodes.isEmpty()) {
            Text(
                "В проекте пока нет нодов. Создайте первый экран, затем добавьте на него ассеты или текст.",
                style = MaterialTheme.typography.bodyMedium,
                color = FallenTheme.colors.textSecondary,
            )
        } else {
            NodesWorkspace(
                nodes = nodes,
                transitions = state.transitions,
                showConnections = graphMode,
                nodeContent = nodeContent,
            )
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxWidth >= 840.dp) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) { workspace() }
                Column(
                    modifier = Modifier.width(320.dp).fillMaxHeight()
                        .verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                ) { actionEditor() }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // The selected instance's action is reachable without scrolling past every node.
                NodesHeader(
                    nodeCount = nodes.size,
                    transitionCount = state.transitions.size,
                    graphMode = graphMode,
                    onGraphMode = { graphMode = it },
                    onCreate = { showCreate = true },
                    onReturnToCanvas = onReturnToCanvas,
                )
                actionEditor()
                if (nodes.isEmpty()) {
                    Text("Создайте первый нод — экран вашего интерфейса.")
                } else {
                    NodesWorkspace(
                        nodes = nodes,
                        transitions = state.transitions,
                        showConnections = graphMode,
                        nodeContent = nodeContent,
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateNodeDialog(
            suggestedName = "Экран ${nodes.size + 1}",
            canvasWidth = state.canvas.w,
            canvasHeight = state.canvas.h,
            onDismiss = { showCreate = false },
            onCreate = { name, width, height ->
                onCreateNode(name, width, height)
                showCreate = false
            },
        )
    }
    nodes.firstOrNull { it.id == renameId }?.let { node ->
        RenameNodeDialog(
            node = node,
            onDismiss = { renameId = null },
            onRename = { name -> onRenameNode(node.id, name); renameId = null },
        )
    }
    nodes.firstOrNull { it.id == duplicateId }?.let { node ->
        AlertDialog(
            onDismissRequest = { duplicateId = null },
            title = { Text("Дублировать нод?") },
            text = {
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(node.name.ifBlank { "Без имени" })
                    Text("Будут скопированы холст, элементы и их действия. Копия получит собственные экземпляры элементов.")
                }
            },
            confirmButton = {
                TextButton(onClick = { onDuplicateNode(node.id); duplicateId = null }) { Text("Дублировать") }
            },
            dismissButton = { TextButton(onClick = { duplicateId = null }) { Text("Отмена") } },
        )
    }
    nodes.firstOrNull { it.id == deleteId }?.let { node ->
        val connectionCount = state.transitions.count { it.fromNodeId == node.id || it.toNodeId == node.id }
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Удалить нод?") },
            text = {
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(node.name.ifBlank { "Без имени" })
                    Text("Будут удалены элементы этого нода (${node.elements.size}) и связанные переходы ($connectionCount).")
                    if (node.id == state.startNodeId) Text("Это стартовый нод. Старт будет переназначен оставшемуся экрану.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onDeleteNode(node.id); deleteId = null },
                    enabled = nodes.size > 1,
                ) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Отмена") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NodesHeader(
    nodeCount: Int,
    transitionCount: Int,
    graphMode: Boolean,
    onGraphMode: (Boolean) -> Unit,
    onCreate: () -> Unit,
    onReturnToCanvas: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Ноды проекта", style = MaterialTheme.typography.titleLarge)
        Text(
            "$nodeCount экранов · $transitionCount действий",
            style = MaterialTheme.typography.bodySmall,
            color = FallenTheme.colors.textSecondary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(onClick = onCreate, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Создать нод")
            }
            OutlinedButton(onClick = onReturnToCanvas, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("На холст")
            }
            FilterChip(
                selected = graphMode,
                onClick = { onGraphMode(true) },
                label = { Text("Граф") },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            FilterChip(
                selected = !graphMode,
                onClick = { onGraphMode(false) },
                label = { Text("Список") },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        Text(
            if (graphMode) "Сплошная стрелка — переход на экран. Пунктир — «Назад» по истории Preview. Карточки расположены автоматически."
            else "Откройте нод, чтобы редактировать его холст. Все действия по нажатию перечислены на карточках.",
            style = MaterialTheme.typography.bodySmall,
            color = FallenTheme.colors.textSecondary,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NodeCard(
    node: ScreenNode,
    nodes: List<ScreenNode>,
    outgoing: List<NodeTransition>,
    isActive: Boolean,
    isStart: Boolean,
    canDelete: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onSetStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FallenTheme.colors
    val shape = RoundedCornerShape(16.dp)
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.border(1.dp, if (isActive) colors.selection else colors.divider, shape),
        shape = shape,
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
        else colors.surfaceElevated,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // No fixed card height or maxLines: user labels remain readable at large font sizes.
                Text(
                    node.name.ifBlank { "Без имени" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(top = 10.dp),
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Действия нода ${node.name}")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Переименовать") },
                            onClick = { menuExpanded = false; onRename() },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Дублировать") },
                            onClick = { menuExpanded = false; onDuplicate() },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        )
                        if (!isStart) {
                            DropdownMenuItem(
                                text = { Text("Сделать стартовым") },
                                onClick = { menuExpanded = false; onSetStart() },
                                leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (canDelete) "Удалить" else "Последний нод нельзя удалить") },
                            onClick = { menuExpanded = false; onDelete() },
                            enabled = canDelete,
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        )
                    }
                }
            }
            Text(
                "${node.canvas.w} × ${node.canvas.h} · ${node.elements.size} элементов",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isStart) NodeStatus("Старт", true)
                if (isActive) NodeStatus("Редактируется", false)
            }
            OutlinedButton(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics { contentDescription = "Открыть холст нода ${node.name}" },
            ) { Text("Открыть холст") }
            HorizontalDivider(color = colors.divider)
            if (outgoing.isEmpty()) {
                Text("Нет действий по нажатию", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
            } else {
                outgoing.forEach { transition ->
                    val element = node.elements.firstOrNull { it.id == transition.elementId }
                    Text(
                        "${element?.name?.ifBlank { "Элемент ${transition.elementId}" } ?: "Элемент ${transition.elementId}"}\n" +
                            transitionDescription(transition, nodes),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeStatus(label: String, start: Boolean) {
    Row(
        modifier = Modifier.background(
            if (start) FallenTheme.colors.success.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            RoundedCornerShape(6.dp),
        ).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (start) Icon(Icons.Outlined.Flag, contentDescription = null, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Actions belong to an element ID, never to an asset ID or source bitmap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ElementTransitionEditor(
    element: CanvasElement?,
    nodes: List<ScreenNode>,
    transition: NodeTransition?,
    onSetTransition: (String, String) -> Unit,
    onSetBack: (String) -> Unit,
    onClear: (String) -> Unit,
) {
    val colors = FallenTheme.colors
    var expanded by rememberSaveable(element?.id) { mutableStateOf(element != null) }
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceElevated) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Действие по нажатию", style = MaterialTheme.typography.titleSmall)
            if (element == null) {
                Text(
                    "Выберите экземпляр на холсте или в «Слоях», затем откройте «Ноды», чтобы назначить переход.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            } else {
                Text(element.name.ifBlank { "Элемент ${element.id}" }, style = MaterialTheme.typography.bodyMedium)
                Text(
                    transitionDescription(transition, nodes),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (expanded) "Свернуть настройки" else "Настроить действие")
                }
                if (expanded) {
                    var targetId by rememberSaveable(element.id, transition?.id, transition?.toNodeId) {
                        mutableStateOf(transition?.toNodeId ?: nodes.firstOrNull()?.id)
                    }
                    var chooseTarget by rememberSaveable(element.id) { mutableStateOf(false) }
                    val target = nodes.firstOrNull { it.id == targetId } ?: nodes.firstOrNull()
                    Text("Целевой нод", style = MaterialTheme.typography.labelMedium)
                    OutlinedButton(
                        onClick = { chooseTarget = true },
                        enabled = nodes.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(target?.name?.ifBlank { "Без имени" } ?: "Нет нодов", modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Выбрать целевой нод")
                    }
                    Button(
                        onClick = { target?.let { onSetTransition(element.id, it.id) } },
                        enabled = target != null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Назначить переход") }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { onSetBack(element.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Назад")
                        }
                        if (transition != null) {
                            TextButton(onClick = { onClear(element.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Очистить действие")
                            }
                        }
                    }
                    Text(
                        "Нажатие работает только в Preview. В редакторе касание выбирает элемент. Действие относится к этому экземпляру — копии одной картинки могут вести на разные экраны.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    if (chooseTarget) {
                        AlertDialog(
                            onDismissRequest = { chooseTarget = false },
                            title = { Text("Куда перейти?") },
                            text = {
                                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                                    nodes.forEach { node ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                                .selectable(
                                                    selected = target?.id == node.id,
                                                    role = Role.RadioButton,
                                                    onClick = { targetId = node.id; chooseTarget = false },
                                                ).padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RadioButton(selected = target?.id == node.id, onClick = null)
                                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                                Text(node.name.ifBlank { "Без имени" })
                                                Text("${node.canvas.w} × ${node.canvas.h}", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { chooseTarget = false }) { Text("Закрыть") } },
                        )
                    }
                }
            }
        }
    }
}

private fun transitionDescription(transition: NodeTransition?, nodes: List<ScreenNode>): String {
    if (transition == null) return "Действие не назначено"
    return when (transition.kind) {
        TransitionKind.OPEN_SCREEN -> {
            val target = nodes.firstOrNull { it.id == transition.toNodeId }
            "Переход → ${target?.name?.ifBlank { "Без имени" } ?: "недоступный нод"}"
        }
        TransitionKind.BACK -> "Назад → предыдущий экран в истории Preview"
    }
}

@Composable
private fun CreateNodeDialog(
    suggestedName: String,
    canvasWidth: Int,
    canvasHeight: Int,
    onDismiss: () -> Unit,
    onCreate: (String, Int, Int) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(suggestedName) }
    var width by rememberSaveable { mutableStateOf(canvasWidth.toString()) }
    var height by rememberSaveable { mutableStateOf(canvasHeight.toString()) }
    val parsedWidth = width.toIntOrNull()
    val parsedHeight = height.toIntOrNull()
    val validSize = parsedWidth != null && parsedHeight != null &&
        parsedWidth in 100..10000 && parsedHeight in 100..10000
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать нод") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя экрана") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    maxLines = 3,
                )
                Text("Начальный размер взят с текущего холста. От 100 до 10 000 px на сторону.", style = MaterialTheme.typography.bodySmall)
                // Stacked fields keep both labels readable on narrow phones / large font scales.
                OutlinedTextField(
                    value = width,
                    onValueChange = { width = it.filter(Char::isDigit).take(5) },
                    label = { Text("Ширина, px") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = parsedWidth == null || parsedWidth !in 100..10000,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it.filter(Char::isDigit).take(5) },
                    label = { Text("Высота, px") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = parsedHeight == null || parsedHeight !in 100..10000,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (validSize) onCreate(name.trim(), requireNotNull(parsedWidth), requireNotNull(parsedHeight)) },
                enabled = name.isNotBlank() && validSize,
            ) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun RenameNodeDialog(node: ScreenNode, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by rememberSaveable(node.id) { mutableStateOf(node.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переименовать нод") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Имя экрана") },
                minLines = 1,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(name.trim()) }, enabled = name.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
