package com.fallen.studio.data.model

/**
 * Каноническая модель проекта v4. Не генерирует случайные ID, не удаляет элементы,
 * переходы или ресурсы и не меняет метаданные. Отсутствующие assets/fonts не являются
 * поводом выкинуть ссылающийся на них элемент: восстановить ресурс можно позднее.
 */
object ProjectGraph {
    const val VERSION = 4

    private const val FIRST_NODE_ID = "screen_1"
    private val generatedElementId = Regex("el[0-9]+")

    /**
     * v1..3 -> один screen_1 с исходным canvas/elements. Новый объект v4 без nodes
     * получает такой же экран; при импорте сериализатор требует непустые nodes v4.
     * Null start выбирает первый экран, null active — start. Явные битые ссылки
     * не исправляются. Корневые canvas/elements всегда берутся из active node.
     *
     * @throws IllegalArgumentException для неподдерживаемой версии, неоднозначного
     * legacy-документа, невалидной геометрии/типов/ID/ссылок или исчерпанного counter.
     */
    fun normalize(project: FallenProject): FallenProject {
        require(project.version in 1..VERSION) { "Unsupported project version: ${project.version}" }
        if (project.version < VERSION) {
            // Данные графа под legacy-version нельзя молча потерять при миграции.
            require(project.nodes.isEmpty() && project.transitions.isEmpty() &&
                project.startNodeId == null && project.activeNodeId == null) {
                "Legacy projects cannot contain screen graph data"
            }
        }

        val nodes = if (project.nodes.isEmpty()) {
            listOf(ScreenNode(FIRST_NODE_ID, "Screen 1", project.canvas, project.elements))
        } else {
            project.nodes
        }
        val nodesById = linkedMapOf<String, ScreenNode>()
        val elementIdsByNode = mutableMapOf<String, Set<String>>()
        var counter = maxOf(0, project.counter)

        for (node in nodes) {
            require(node.id.isNotBlank()) { "Screen ID must not be blank" }
            require(node.id !in nodesById) { "Duplicate screen ID: ${node.id}" }
            require(node.canvas.w > 0 && node.canvas.h > 0) {
                "Invalid canvas size in screen ${node.id}"
            }
            nodesById[node.id] = node
            val elementIds = mutableSetOf<String>()
            for (element in node.elements) {
                require(element.id.isNotBlank()) { "Element ID must not be blank in screen ${node.id}" }
                require(elementIds.add(element.id)) {
                    "Duplicate element ID ${element.id} in screen ${node.id}"
                }
                validateElement(element)
                if (generatedElementId.matches(element.id)) {
                    val number = requireNotNull(element.id.substring(2).toIntOrNull()) {
                        "Element ID exceeds the supported counter range: ${element.id}"
                    }
                    counter = maxOf(counter, number)
                }
            }
            elementIdsByNode[node.id] = elementIds
        }
        // Редактор создаёт el(counter + 1); не допускаем переполнение Int и коллизии.
        require(counter < Int.MAX_VALUE) { "Element counter is exhausted" }

        val startNodeId = project.startNodeId ?: nodes.first().id
        require(startNodeId in nodesById) { "Unknown start screen: $startNodeId" }
        val activeNodeId = project.activeNodeId ?: startNodeId
        val activeNode = requireNotNull(nodesById[activeNodeId]) { "Unknown active screen: $activeNodeId" }

        val transitionIds = mutableSetOf<String>()
        val clickSources = mutableSetOf<Pair<String, String>>()
        for (transition in project.transitions) {
            require(transition.id.isNotBlank()) { "Transition ID must not be blank" }
            require(transitionIds.add(transition.id)) { "Duplicate transition ID: ${transition.id}" }
            val elementIds = requireNotNull(elementIdsByNode[transition.fromNodeId]) {
                "Unknown transition source screen: ${transition.fromNodeId}"
            }
            require(transition.elementId in elementIds) {
                "Unknown element ${transition.elementId} in screen ${transition.fromNodeId}"
            }
            require(clickSources.add(transition.fromNodeId to transition.elementId)) {
                "Multiple click actions for ${transition.fromNodeId}/${transition.elementId}"
            }
            when (transition.kind) {
                TransitionKind.OPEN_SCREEN -> require(transition.toNodeId != null &&
                    transition.toNodeId in nodesById) {
                    "Unknown OPEN_SCREEN target: ${transition.toNodeId}"
                }
                TransitionKind.BACK -> require(transition.toNodeId == null) {
                    "BACK must not contain a target screen"
                }
            }
        }

        return project.copy(
            version = VERSION,
            nodes = nodes,
            startNodeId = startNodeId,
            activeNodeId = activeNodeId,
            canvas = activeNode.canvas,
            elements = activeNode.elements,
            counter = counter,
        )
    }

    private fun validateElement(element: CanvasElement) {
        require(element.isText || element.isImage) { "Unsupported element type: ${element.type}" }
        require(element.w >= 0f && element.h >= 0f) { "Negative element size: ${element.id}" }
        // Координаты вне холста, нулевые размеры и произвольные углы сохраняются точно.
        val numbers = listOf(
            element.x, element.y, element.w, element.h, element.opacity, element.rotation,
            element.strokeWidth, element.lineHeight, element.letterSpacing, element.shadowBlur,
            element.shadowOpacity, element.shadowX, element.shadowY,
        )
        require(numbers.all { it == null || it.isFinite() }) {
            "Non-finite element geometry/style: ${element.id}"
        }
    }
}
