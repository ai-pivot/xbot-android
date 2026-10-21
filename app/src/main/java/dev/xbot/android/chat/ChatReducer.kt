package dev.xbot.android.chat

import dev.xbot.android.data.AskUserQuestion
import dev.xbot.android.data.HistoryIteration
import dev.xbot.android.data.HistoryMessage
import dev.xbot.android.data.HistoryResponse
import dev.xbot.android.data.ProgressEvent
import dev.xbot.android.data.ToolProgress
import dev.xbot.android.data.WsMessage
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure chat state machine.
 *
 * Mirrors the parts of the web client's `chat/reduce.ts` that matter on a phone:
 * one turn per turn id, iterations merged by number, stream checkpoints replace
 * (deltas append), and a turn-ending signal never discards content that has
 * already been rendered.
 */
object ChatReducer {

    fun empty(chatId: String, channel: String, title: String = ""): ChatUiState =
        ChatUiState(chatId = chatId, channel = channel, title = title, loading = true)

    fun withHistory(state: ChatUiState, response: HistoryResponse): ChatUiState {
        val turns = LinkedHashMap<Long, TurnUi>()
        response.messages.forEach { message -> mergeHistoryMessage(turns, message) }

        // A completion-only snapshot (`{phase:"done", iteration:0, turn_id:0}`)
        // is NOT a running turn — it is how the server restores todos/goal for an
        // idle session. Treating "active_progress != null" as busy made every
        // idle session render "思考中…" forever (user report 2026-10-20).
        val active = response.activeProgress
        val activeRunning = active != null && active.isRunning()

        var next = state.copy(
            loading = false,
            error = null,
            turns = turns.values.sortedBy { it.turnId },
            busy = response.processing || activeRunning,
            hasMore = response.hasMore,
            oldestId = response.oldestId,
            loadingMore = false,
            loadMoreError = null,
            needsReload = false,
            pendingUserText = null,
        )
        if (active != null && (active.turnId > 0L || activeRunning)) {
            next = applyProgress(next, active)
        }
        return next
    }

    /**
     * A latest-page reload must not discard pages the user already loaded by
     * scrolling up. Merge the new page into the existing turns and keep the
     * earliest pagination cursor; otherwise SSE resync/reload makes old history
     * disappear and the UI immediately re-fetches it (the reload/loadMore loop).
     */
    fun mergeLatestHistory(state: ChatUiState, response: HistoryResponse): ChatUiState {
        val turns = LinkedHashMap<Long, TurnUi>()
        state.turns.forEach { turns[it.turnId] = it }
        response.messages.forEach { message -> mergeHistoryMessage(turns, message) }

        val keepOlderCursor = state.oldestId > 0L &&
            response.oldestId > 0L &&
            state.oldestId < response.oldestId

        val active = response.activeProgress
        val activeRunning = active != null && active.isRunning()

        var next = state.copy(
            turns = turns.values.sortedBy { it.turnId },
            loading = false,
            error = null,
            busy = response.processing || activeRunning,
            hasMore = if (keepOlderCursor) state.hasMore else response.hasMore,
            oldestId = if (keepOlderCursor) state.oldestId else response.oldestId,
            loadingMore = false,
            loadMoreError = null,
            needsReload = false,
            pendingUserText = null,
        )
        if (active != null && (active.turnId > 0L || activeRunning)) {
            next = applyProgress(next, active)
        }
        return next
    }

    /** Merge an older history page (loadMore) without disturbing the live tail. */
    fun prependHistory(state: ChatUiState, response: HistoryResponse): ChatUiState {
        val turns = LinkedHashMap<Long, TurnUi>()
        state.turns.forEach { turns[it.turnId] = it }
        response.messages.forEach { message -> mergeHistoryMessage(turns, message) }
        return state.copy(
            turns = turns.values.sortedBy { it.turnId },
            hasMore = response.hasMore,
            oldestId = response.oldestId,
            loading = false,
            loadingMore = false,
            loadMoreError = null,
        )
    }

    fun sendAccepted(
        state: ChatUiState,
        text: String,
        turnId: Long,
        queued: Boolean,
    ): ChatUiState {
        if (turnId <= 0L) {
            return state.copy(pendingUserText = text, error = null)
        }
        val existing = state.turns.firstOrNull { it.turnId == turnId }
        val turn = (existing ?: TurnUi(turnId = turnId, userText = text))
            .copy(userText = existing?.userText?.ifBlank { text } ?: text)
        return state.copy(
            turns = upsertTurn(state.turns, turn),
            pendingUserText = null,
            busy = existing?.let { state.busy } ?: true,
            liveTurnId = turnId,
            error = null,
        )
    }

    fun applyEvent(state: ChatUiState, message: WsMessage): ChatUiState {
        return when (message.type) {
            "progress_structured",
            "progress",
            "sync_progress",
            "stream_content",
            "turn_started",
            -> message.progress?.let { applyProgress(state, it) } ?: state

            "text" -> applyText(state, message)
            "user_echo" -> applyUserEcho(state, message)
            "inject_user" -> applyUserEcho(state, message)
            "session" -> applySession(state, message)
            "ask_user" -> applyAskUser(state, message)
            "ask_user_resolved" -> applyAskUserResolved(state, message)
            "queue_state" -> message.queueState?.let { qs ->
                state.copy(queue = qs.items)
            } ?: state

            "resync_required" -> state.copy(needsReload = true)
            else -> state
        }
    }

    fun applyProgress(state: ChatUiState, progress: ProgressEvent): ChatUiState {
        var next = state
        var turnId = progress.turnId.takeIf { it > 0L } ?: state.liveTurnId

        progress.turnStart?.let { start ->
            if (turnId <= 0L) turnId = (state.turns.maxOfOrNull { it.turnId } ?: 0L) + 1
            next = ensureTurn(next, turnId, start.content)
            next = next.copy(pendingUserText = null, liveTurnId = turnId, busy = true)
        }

        if (turnId <= 0L) {
            // No turn to attach to. A terminal snapshot still carries state
            // (e.g. a completion-only `{phase:"done"}` snapshot) and MUST clear
            // the busy flag; otherwise the next history load leaves the UI
            // stuck on "思考中…".
            return if (progress.isTerminal()) {
                next.copy(busy = false, liveTurnId = 0L, liveIteration = 0)
            } else {
                next
            }
        }

        if (progress.iterationHistory.isNotEmpty()) {
            next = updateTurn(next, turnId) { turn ->
                turn.copy(
                    iterations = mergeIterations(
                        turn.iterations,
                        progress.iterationHistory.map { it.toUi() },
                    ),
                )
            }
        }

        val currentTurn = next.turns.firstOrNull { it.turnId == turnId }
        val fallbackIteration = currentTurn?.iterations?.maxOfOrNull { it.index } ?: 0
        val iterationIndex = when {
            progress.iteration > 0 -> progress.iteration
            fallbackIteration > 0 -> fallbackIteration
            else -> 1
        }

        next = ensureTurn(next, turnId, "")
        next = updateTurn(next, turnId) { turn ->
            val existing = turn.iterations.firstOrNull { it.index == iterationIndex }
            var iteration = existing ?: IterationUi(iterationIndex)

            val incomingTools = buildList {
                addAll(progress.activeTools)
                addAll(progress.completedTools)
                progress.toolCalls.forEach { call ->
                    add(
                        ToolProgress(
                            name = call.name,
                            args = call.args,
                            status = call.status,
                            elapsedMs = call.elapsed,
                            callId = call.id,
                            iteration = iterationIndex,
                        ),
                    )
                }
            }.map { it.toUi(iterationIndex) }
            if (incomingTools.isNotEmpty()) {
                iteration = iteration.copy(tools = mergeTools(iteration.tools, incomingTools))
            }

            when {
                progress.streamContent.isNotEmpty() -> iteration =
                    iteration.copy(content = progress.streamContent)

                progress.streamDelta.isNotEmpty() -> iteration =
                    iteration.copy(content = iteration.content + progress.streamDelta)

                progress.content.isNotEmpty() -> iteration =
                    iteration.copy(content = progress.content)
            }

            when {
                progress.reasoningStreamContent.isNotEmpty() -> iteration =
                    iteration.copy(reasoning = progress.reasoningStreamContent)

                progress.reasoningStreamDelta.isNotEmpty() -> iteration =
                    iteration.copy(reasoning = iteration.reasoning + progress.reasoningStreamDelta)

                progress.reasoning.isNotEmpty() -> iteration =
                    iteration.copy(reasoning = progress.reasoning)
            }

            val finished = progress.phase == "done" ||
                progress.phase == "cancelled" ||
                progress.phase == "error"
            if (finished) iteration = iteration.copy(done = true)

            val finalText = if (finished && progress.content.isNotBlank()) {
                turn.finalText.ifBlank { progress.content }
            } else {
                turn.finalText
            }
            turn.copy(
                iterations = upsertIteration(turn.iterations, iteration),
                finalText = finalText,
            )
        }

        val finished = progress.phase == "done" ||
            progress.phase == "cancelled" ||
            progress.phase == "error"
        return next.copy(
            busy = !finished,
            liveTurnId = if (finished) 0L else turnId,
            liveIteration = if (finished) 0 else iterationIndex,
        )
    }

    fun applyText(state: ChatUiState, message: WsMessage): ChatUiState {
        var turnId = message.turnId
        if (turnId <= 0L) turnId = state.liveTurnId
        if (turnId <= 0L) {
            // Command replies (turn_id=0) are rendered as a standalone turn so
            // `!cmd` output does not vanish; the reducer keeps them at the end.
            if (message.content.isBlank()) return state
            val id = (state.turns.maxOfOrNull { it.turnId } ?: 0L) + 1
            val turn = TurnUi(turnId = id, finalText = message.content, cancelled = message.cancelled)
            return state.copy(turns = upsertTurn(state.turns, turn), busy = false)
        }
        val next = ensureTurn(state, turnId, "")
            .let { s ->
                updateTurn(s, turnId) { turn ->
                    turn.copy(
                        finalText = message.content.ifBlank { turn.finalText },
                        cancelled = message.cancelled,
                    )
                }
            }
        return next.copy(busy = false, liveTurnId = 0L, liveIteration = 0)
    }

    fun applyUserEcho(state: ChatUiState, message: WsMessage): ChatUiState {
        val turnId = message.turnId
        if (turnId <= 0L) return state
        val text = message.content
        val next = ensureTurn(state, turnId, text)
            .let { s ->
                updateTurn(s, turnId) { turn ->
                    turn.copy(userText = turn.userText.ifBlank { text })
                }
            }
        return next.copy(pendingUserText = null, busy = true, liveTurnId = turnId)
    }

    fun applySession(state: ChatUiState, message: WsMessage): ChatUiState {
        val event = message.session ?: return state
        if (event.chatId.isNotBlank() && state.chatId.isNotBlank() && event.chatId != state.chatId) {
            return state
        }
        return when (event.action) {
            "busy" -> state.copy(busy = true)
            "idle" -> state.copy(
                busy = false,
                liveTurnId = 0L,
                liveIteration = 0,
            )
            "deleted" -> state.copy(busy = false)
            else -> state
        }
    }

    fun applyAskUser(state: ChatUiState, message: WsMessage): ChatUiState {
        val progress = message.progress
        var questions = progress?.questions.orEmpty()
        val requestId = progress?.requestId.orEmpty()
        if (questions.isEmpty()) {
            // AskUserEvent carries questions as a JSON-encoded STRING.
            questions = parseQuestions(message.content)
        }
        if (questions.isEmpty()) return state
        return state.copy(
            askUser = AskUserUi(
                requestId = requestId.ifBlank { parseRequestId(message.content) },
                questions = questions,
            ),
        )
    }

    fun applyAskUserResolved(state: ChatUiState, message: WsMessage): ChatUiState {
        val current = state.askUser ?: return state
        if (message.requestId.isNotBlank() &&
            current.requestId.isNotBlank() &&
            message.requestId != current.requestId
        ) {
            return state
        }
        return state.copy(askUser = null)
    }

    fun dismissAskUser(state: ChatUiState): ChatUiState = state.copy(askUser = null)

    // ------------------------------------------------------------------
    // History conversion helpers
    // ------------------------------------------------------------------

    private fun mergeHistoryMessage(turns: MutableMap<Long, TurnUi>, message: HistoryMessage) {
        if (message.turnId <= 0L) return
        val turn = turns[message.turnId] ?: TurnUi(turnId = message.turnId)

        when (message.role) {
            "user" -> {
                val historyId = message.historyId.takeIf { it > 0L } ?: message.id
                turns[message.turnId] = turn.copy(
                    userText = message.content.ifBlank { turn.userText },
                    userHistoryId = historyId.takeIf { it > 0L } ?: turn.userHistoryId,
                )
            }

            "assistant" -> {
                var merged = turn
                val turnUser = message.turnUser
                if (turnUser != null) {
                    val userHistoryId = turnUser.historyId.takeIf { it > 0L } ?: turnUser.id
                    merged = merged.copy(
                        userText = merged.userText.ifBlank { turnUser.content },
                        userHistoryId = merged.userHistoryId.takeIf { it > 0L }
                            ?: userHistoryId,
                    )
                }
                if (message.iterations.isNotEmpty()) {
                    merged = merged.copy(
                        iterations = mergeIterations(
                            merged.iterations,
                            message.iterations.map { it.toUi() },
                        ),
                    )
                }
                if (message.content.isNotBlank()) {
                    merged = merged.copy(finalText = message.content)
                }
                turns[message.turnId] = merged
            }
        }
    }

    private fun HistoryIteration.toUi(): IterationUi = IterationUi(
        index = if (iteration > 0) iteration else 1,
        content = content,
        reasoning = reasoning,
        tools = tools.map { it.toUi(iteration) },
        done = true,
    )

    private fun ProgressEvent.toUi(): IterationUi = IterationUi(
        index = if (iteration > 0) iteration else 1,
        content = content,
        reasoning = reasoning,
        tools = buildList {
            addAll(activeTools)
            addAll(completedTools)
        }.map { it.toUi(iteration) },
        done = phase == "done",
    )

    private fun ToolProgress.toUi(fallbackIteration: Int): ToolUi {
        val key = callId.ifBlank {
            "${name}_${iteration.takeIf { it > 0 } ?: fallbackIteration}_$label"
        }
        return ToolUi(
            key = key,
            name = name,
            label = label,
            status = status,
            args = args,
            detail = detail,
            summary = summary,
            elapsedMs = elapsedMs,
            iteration = iteration.takeIf { it > 0 } ?: fallbackIteration,
        )
    }

    private fun mergeIterations(
        existing: List<IterationUi>,
        incoming: List<IterationUi>,
    ): List<IterationUi> {
        if (incoming.isEmpty()) return existing
        val map = LinkedHashMap<Int, IterationUi>()
        existing.forEach { map[it.index] = it }
        incoming.forEach { candidate ->
            val previous = map[candidate.index]
            map[candidate.index] = if (previous == null) {
                candidate
            } else {
                previous.copy(
                    content = candidate.content.ifBlank { previous.content },
                    reasoning = candidate.reasoning.ifBlank { previous.reasoning },
                    tools = mergeTools(previous.tools, candidate.tools),
                    done = previous.done || candidate.done,
                )
            }
        }
        return map.values.sortedBy { it.index }
    }

    private fun mergeTools(existing: List<ToolUi>, incoming: List<ToolUi>): List<ToolUi> {
        if (incoming.isEmpty()) return existing
        val map = LinkedHashMap<String, ToolUi>()
        existing.forEach { map[it.key] = it }
        incoming.forEach { candidate ->
            val previous = map[candidate.key]
            map[candidate.key] = if (previous == null) {
                candidate
            } else {
                previous.copy(
                    name = candidate.name.ifBlank { previous.name },
                    label = candidate.label.ifBlank { previous.label },
                    status = candidate.status.ifBlank { previous.status },
                    args = candidate.args.ifBlank { previous.args },
                    detail = candidate.detail.ifBlank { previous.detail },
                    summary = candidate.summary.ifBlank { previous.summary },
                    elapsedMs = maxOf(previous.elapsedMs, candidate.elapsedMs),
                    iteration = candidate.iteration.takeIf { it > 0 } ?: previous.iteration,
                )
            }
        }
        return map.values.toList()
    }

    private fun upsertIteration(
        iterations: List<IterationUi>,
        iteration: IterationUi,
    ): List<IterationUi> {
        val map = LinkedHashMap<Int, IterationUi>()
        iterations.forEach { map[it.index] = it }
        val previous = map[iteration.index]
        map[iteration.index] = if (previous == null) {
            iteration
        } else {
            previous.copy(
                content = iteration.content.ifBlank { previous.content },
                reasoning = iteration.reasoning.ifBlank { previous.reasoning },
                tools = mergeTools(previous.tools, iteration.tools),
                done = previous.done || iteration.done,
            )
        }
        return map.values.sortedBy { it.index }
    }

    private fun ensureTurn(state: ChatUiState, turnId: Long, userText: String): ChatUiState {
        if (turnId <= 0L) return state
        val existing = state.turns.firstOrNull { it.turnId == turnId }
        val turn = if (existing == null) {
            TurnUi(turnId = turnId, userText = userText)
        } else {
            existing.copy(userText = existing.userText.ifBlank { userText })
        }
        return state.copy(turns = upsertTurn(state.turns, turn))
    }

    private fun updateTurn(
        state: ChatUiState,
        turnId: Long,
        transform: (TurnUi) -> TurnUi,
    ): ChatUiState {
        val index = state.turns.indexOfFirst { it.turnId == turnId }
        if (index < 0) return state
        val updated = state.turns.toMutableList()
        updated[index] = transform(updated[index])
        return state.copy(turns = updated)
    }

    private fun upsertTurn(turns: List<TurnUi>, turn: TurnUi): List<TurnUi> {
        val index = turns.indexOfFirst { it.turnId == turn.turnId }
        if (index < 0) return turns + turn
        val updated = turns.toMutableList()
        updated[index] = turn
        return updated
    }

    private fun parseQuestions(raw: String): List<AskUserQuestion> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val obj = Json.parseToJsonElement(raw).jsonObject
            val questionsRaw = obj["questions"]?.jsonPrimitive?.content ?: return emptyList()
            Json { ignoreUnknownKeys = true }.decodeFromString<List<AskUserQuestion>>(questionsRaw)
        }.getOrDefault(emptyList())
    }

    private fun parseRequestId(raw: String): String = runCatching {
        Json.parseToJsonElement(raw).jsonObject["request_id"]?.jsonPrimitive?.content.orEmpty()
    }.getOrDefault("")
}

private fun ProgressEvent.isTerminal(): Boolean =
    phase == "done" || phase == "cancelled" || phase == "error"

private fun ProgressEvent.isRunning(): Boolean =
    phase.isNotBlank() && !isTerminal()
