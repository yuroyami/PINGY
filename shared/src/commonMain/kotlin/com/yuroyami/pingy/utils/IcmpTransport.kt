package com.yuroyami.pingy.utils

/**
 * Everything the engine needs from the platform, behind one interface.
 *
 * The engine's hardest bugs live at these boundaries: a resolver that blocks
 * past cancellation, a socket that dies with probes in the air, a power query
 * that never runs. Testing those against the real network is not repeatable,
 * so the boundary is injectable and [PlatformTransport] is the production wiring.
 */
interface IcmpTransport {
    fun available(): Boolean
    fun resolve(host: String): String?
    fun open(ipv4: String): Int
    fun send(fd: Int, session: Long, seq: Int, payloadSize: Int): Long
    fun await(fd: Int, session: Long, budgetMs: Int): Long
    fun close(fd: Int)
    fun powerState(): PowerState
}

/** The real platform calls. Every target's actuals, gathered in one place. */
object PlatformTransport : IcmpTransport {
    override fun available(): Boolean = icmpTransportAvailable()
    override fun resolve(host: String): String? = resolveHostToIpv4(host)
    override fun open(ipv4: String): Int = openIcmpSocket(ipv4)
    override fun send(fd: Int, session: Long, seq: Int, payloadSize: Int): Long =
        icmpSendProbe(fd, session, seq, payloadSize)
    override fun await(fd: Int, session: Long, budgetMs: Int): Long =
        icmpAwaitReply(fd, session, budgetMs)
    override fun close(fd: Int) = closeIcmpSocket(fd)
    override fun powerState(): PowerState = currentPowerState()
}
