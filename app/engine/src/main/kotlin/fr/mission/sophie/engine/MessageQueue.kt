package fr.mission.sophie.engine

/**
 * Decides which message is displayed/voiced (spec 7.1):
 * - one voice at a time; the bubble stays max(6 s, audio + 1 s);
 * - a higher priority message interrupts a lower one;
 * - an idle message never interrupts anything and is dropped if something is playing or queued;
 * - otherwise messages are queued by priority.
 */
class MessageQueue(
    /** Audio duration in ms for a message, or null when there is no audio file. */
    private val audioDurationMs: (Message) -> Long?,
    private val minDisplayMs: Long = 6000,
    private val maxQueue: Int = 8,
) {
    data class Playing(val message: Message, val startedAt: Long, val until: Long, val hasAudio: Boolean)

    sealed interface Action {
        data class Start(val playing: Playing) : Action
        data class Stop(val message: Message, val interrupted: Boolean) : Action
        data class Dropped(val message: Message) : Action
    }

    var current: Playing? = null
        private set
    private val queue = ArrayList<Message>()

    val pending: List<Message> get() = queue.toList()

    fun post(message: Message, now: Long, interrupt: Boolean = false): List<Action> {
        val out = mutableListOf<Action>()
        val cur = current
        when {
            interrupt -> {
                queue.clear()
                if (cur != null) out += Action.Stop(cur.message, interrupted = true)
                out += start(message, now)
            }
            cur == null && queue.isEmpty() -> out += start(message, now)
            message.priority == Priority.IDLE -> out += Action.Dropped(message)
            cur != null && message.priority < cur.message.priority -> {
                out += Action.Stop(cur.message, interrupted = true)
                out += start(message, now)
            }
            else -> {
                val idx = queue.indexOfFirst { it.priority > message.priority }
                if (idx < 0) queue += message else queue.add(idx, message)
                while (queue.size > maxQueue) out += Action.Dropped(queue.removeAt(queue.lastIndex))
                if (cur == null) out += next(now)
            }
        }
        return out
    }

    /** Call regularly; ends the current bubble when its time is over and starts the next one. */
    fun tick(now: Long): List<Action> {
        val cur = current ?: return if (queue.isNotEmpty()) next(now) else emptyList()
        if (now < cur.until) return emptyList()
        current = null
        return listOf(Action.Stop(cur.message, interrupted = false)) + next(now)
    }

    fun clear(): List<Action> {
        queue.clear()
        val cur = current ?: return emptyList()
        current = null
        return listOf(Action.Stop(cur.message, interrupted = true))
    }

    private fun next(now: Long): List<Action> {
        if (queue.isEmpty()) return emptyList()
        return start(queue.removeAt(0), now)
    }

    private fun start(message: Message, now: Long): List<Action> {
        val audio = audioDurationMs(message)
        val display = maxOf(minDisplayMs, (audio ?: 0) + 1000)
        val p = Playing(message, now, now + display, audio != null)
        current = p
        return listOf(Action.Start(p))
    }
}
