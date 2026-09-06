package com.yuroyami.pingy.utils

/**
 * A platform the test writes the script for.
 *
 * [awaits] is played back first, one entry per wait, so a test can hand the
 * engine a socket error or an empty wait at an exact point. Once it runs out,
 * [replyTo] decides whether the oldest outstanding probe answers.
 *
 * `await` returns immediately, which would spin the engine's loop. [idle] lets
 * a JVM test sleep the budget instead; the default is fine for tests that stop
 * the engine as soon as they have their verdict.
 */
class ScriptedTransport(
    private val awaits: ArrayDeque<Long> = ArrayDeque(),
    private val resolveTo: String? = "192.0.2.1",
    private val openResult: Int = 7,
    private val idle: (budgetMs: Int) -> Unit = {},
) : IcmpTransport {

    var sends = 0; private set
    var opens = 0; private set
    var closes = 0; private set
    var resolves = 0; private set
    var powerQueries = 0; private set

    /** Given a probe's sequence and send moment, the receive moment, or null for silence. */
    var replyTo: (seq: Int, sendUsec: Long) -> Long? = { _, _ -> null }

    private val sendUsecBySeq = LinkedHashMap<Int, Long>()

    override fun available(): Boolean = true

    override fun resolve(host: String): String? {
        resolves++
        return resolveTo
    }

    override fun open(ipv4: String): Int {
        opens++
        return openResult
    }

    override fun send(fd: Int, session: Long, seq: Int, payloadSize: Int): Long {
        sends++
        val usec = 1_000L * sends
        sendUsecBySeq[seq] = usec
        return usec
    }

    override fun await(fd: Int, session: Long, budgetMs: Int): Long {
        awaits.removeFirstOrNull()?.let { return it }
        // Copy the key and value out before touching the map. Kotlin/Native
        // invalidates an entry reference as soon as the backing map changes,
        // and reading it afterwards throws.
        val entry = sendUsecBySeq.entries.firstOrNull()
        val seq = entry?.key
        val sendUsec = entry?.value
        val recv = if (seq != null && sendUsec != null) replyTo(seq, sendUsec) else null
        if (seq == null || recv == null) {
            idle(budgetMs)
            return AWAIT_NOTHING
        }
        sendUsecBySeq.remove(seq)
        return ((recv and ((1L shl 47) - 1)) shl 16) or seq.toLong()
    }

    override fun close(fd: Int) {
        closes++
    }

    override fun powerState(): PowerState {
        powerQueries++
        return PowerState.NORMAL
    }
}
