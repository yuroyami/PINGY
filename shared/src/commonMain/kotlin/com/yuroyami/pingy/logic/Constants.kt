package com.yuroyami.pingy.logic

object Constants {

    /**
     * Suggested ping targets in the "IP or Domain" dropdown.
     *
     * Deliberately a mix of:
     *  - Popular public resolvers (1.1.1.1 Cloudflare, 8.8.8.8 Google, 9.9.9.9 Quad9,
     *    208.67.222.222 OpenDNS). Stable, globally anycast, ~tens of ms.
     *  - Typical home-router gateways (192.168.1.1, 192.168.0.1). Local RTT
     *    under a millisecond when the phone is on Wi-Fi; handy for sanity
     *    checks ("is my Wi-Fi bad, or is the whole hop bad?").
     *  - Captive portals (captive.apple.com, connectivitycheck.gstatic.com).
     *    These are the endpoints phones actually hit to answer "does this
     *    Wi-Fi work?", so latency here tracks how connected things feel.
     *
     * Social sites were dropped on purpose: they live behind global CDNs and
     * their pingable IPs move around, which made the RTT history noisy and
     * often ambiguous ("is latency bad, or did the CDN repoint me?").
     */
    val iplist = listOf(
        "1.1.1.1",
        "8.8.8.8",
        "9.9.9.9",
        "208.67.222.222",
        "192.168.1.1",
        "192.168.0.1",
        "captive.apple.com",
        "connectivitycheck.gstatic.com",
    )
}
