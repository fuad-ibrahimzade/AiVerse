package com.aiverse.feature.agent.domain.tool.question

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 管理 ask_user_question 工具的 park-and-resume 流程。
 *
 * 镜像 [com.aiverse.feature.agent.domain.tool.ToolPermissionManager] 的设计：
 * 按 request.id 维护一个待决问题表，多会话并行时每个会话可各自挂起一个问题、互不阻塞；
 * 同一会话内的多个问题由 workflow 串行处理，故一个会话在此表中至多出现一次。
 *
 * 工具侧调用 [awaitAnswer] 挂起；UI 侧观察 [pendingQuestion]（展示用单个请求，取最早进入的）
 * 渲染面板；用户选择后调用 [resolve] 唤醒工具侧，队首出队后自动切到下一道问题。
 */
@Singleton
class AskUserQuestionManager @Inject constructor() {

    /** 所有待决问题，按插入顺序保存（key = 请求 id）。 */
    private val pending = LinkedHashMap<String, Entry>()
    private val lock = Any()

    /** 弹窗展示用：当前应展示的单个问题（取最早进入的），解决后自动切到下一个。 */
    private val _pendingQuestion = MutableStateFlow<PendingUserQuestion?>(null)
    val pendingQuestion: StateFlow<PendingUserQuestion?> = _pendingQuestion.asStateFlow()

    /** 侧边栏用：当前正在等待用户回答的会话 id 集合。 */
    private val _awaitingSessionIds = MutableStateFlow<Set<String>>(emptySet())
    val awaitingSessionIds: StateFlow<Set<String>> = _awaitingSessionIds.asStateFlow()

    /**
     * 工具侧调用：发起一个问题请求，挂起等待用户回答。
     * 不同会话可并行挂起，互不阻塞；同一会话由 workflow 串行处理。
     */
    suspend fun awaitAnswer(question: PendingUserQuestion): UserQuestionAnswer {
        val decision = CompletableDeferred<UserQuestionAnswer>()
        synchronized(lock) {
            pending[question.id] = Entry(question, decision)
            publish()
        }
        try {
            return decision.await()
        } finally {
            synchronized(lock) {
                pending.remove(question.id)
                publish()
            }
        }
    }

    /**
     * UI 侧调用：用户完成选择后，将答案回传，唤醒对应 id 的挂起的 [awaitAnswer]。
     * 队首出队由 [awaitAnswer] 的 finally 块完成。
     */
    fun resolve(id: String, answer: UserQuestionAnswer) {
        val entry = synchronized(lock) { pending[id] } ?: return
        entry.decision.complete(answer)
    }

    /** 取某会话当前的待决问题（停止单个会话时用，避免误取其它会话的问题）。 */
    fun pendingForSession(sessionId: String): PendingUserQuestion? =
        synchronized(lock) { pending.values.firstOrNull { it.question.sessionId == sessionId }?.question }

    /** 在持锁区内调用：把内部表投影到两个对外 StateFlow。 */
    private fun publish() {
        _pendingQuestion.value = pending.values.firstOrNull()?.question
        _awaitingSessionIds.value = pending.values.mapNotNull { it.question.sessionId.ifBlank { null } }.toSet()
    }

    private class Entry(
        val question: PendingUserQuestion,
        val decision: CompletableDeferred<UserQuestionAnswer>
    )
}
