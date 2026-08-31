package com.yuroyami.pingy.logic

/**
 * Turning whatever the user typed into a target we are willing to resolve.
 *
 * This is a trust boundary. The old version stripped four exact scheme prefixes
 * and split on `/`, which let `admin:hunter2@10.0.0.1` through intact, to be
 * persisted to disk and passed to the resolver. It also accepted anything else
 * without complaint, so a typo produced a permanent panel that could only ever
 * report failure.
 */

/** Why a typed target was refused, phrased for a person. */
enum class TargetRejection(val message: String) {
    EMPTY("Type an address first"),
    CREDENTIALS("Remove the username and password from the address"),
    HAS_PORT("Drop the port number; ping addresses a host, not a port"),
    IPV6("IPv6 targets are not supported yet"),
    TOO_LONG("That address is too long"),
    ILLEGAL_CHARACTERS("That address contains characters a host name cannot have"),
    NOT_A_HOST("That does not look like an IP address or domain name"),
}

sealed interface TargetParse {
    data class Valid(val host: String) : TargetParse
    data class Invalid(val reason: TargetRejection) : TargetParse
}

private const val MAX_HOST_LEN = 253

/**
 * Parse and canonicalize user input into a bare host.
 *
 * Accepts `example.com`, `https://example.com/path?q=1`, `1.1.1.1`, and
 * surrounding whitespace. Rejects credentials, ports, IPv6 literals, control
 * characters and anything that is not a plausible host.
 */
fun parseTarget(raw: String): TargetParse {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return TargetParse.Invalid(TargetRejection.EMPTY)
    if (trimmed.any { it.isISOControl() }) {
        return TargetParse.Invalid(TargetRejection.ILLEGAL_CHARACTERS)
    }

    // Strip any scheme, case-insensitively. The old code only matched four
    // exact spellings, so "Https://" survived and became part of the host.
    val schemeEnd = trimmed.indexOf("://")
    var rest = if (schemeEnd in 1..10) trimmed.substring(schemeEnd + 3) else trimmed

    // Drop path, query and fragment.
    rest = rest.substringBefore('/').substringBefore('?').substringBefore('#')
    if (rest.isEmpty()) return TargetParse.Invalid(TargetRejection.NOT_A_HOST)

    // Credentials must never reach the resolver, the store or a log line.
    if (rest.contains('@')) return TargetParse.Invalid(TargetRejection.CREDENTIALS)

    // Bracketed IPv6, or a bare address with multiple colons.
    if (rest.startsWith("[") || rest.count { it == ':' } > 1) {
        return TargetParse.Invalid(TargetRejection.IPV6)
    }
    if (rest.contains(':')) return TargetParse.Invalid(TargetRejection.HAS_PORT)

    val host = rest.removeSuffix(".")   // "example.com." is the same host
    if (host.isEmpty()) return TargetParse.Invalid(TargetRejection.NOT_A_HOST)
    if (host.length > MAX_HOST_LEN) return TargetParse.Invalid(TargetRejection.TOO_LONG)

    if (!isPlausibleHost(host)) return TargetParse.Invalid(TargetRejection.NOT_A_HOST)
    return TargetParse.Valid(host)
}

/**
 * Stable identity for duplicate detection.
 *
 * Case and a trailing dot do not make a different host, and two panels on one
 * address open two sockets to the same peer. That is the exact condition under
 * which a reply can be attributed to the wrong panel, so collapsing spellings
 * here is a correctness measure, not a convenience.
 */
fun canonicalTargetKey(raw: String): String =
    (parseTarget(raw) as? TargetParse.Valid)?.host?.lowercase()
        ?: raw.trim().lowercase()

/**
 * A dotted IPv4 literal, or a syntactically valid DNS name.
 *
 * An all-numeric dotted form is only ever an IPv4 attempt, so `999.1.1.1` and
 * `1.1.1` are refused rather than passed to DNS as unusual host names. Without
 * this they parse as valid labels, become a panel, and fail resolution forever.
 */
private fun isPlausibleHost(host: String): Boolean {
    val labels = host.split('.')
    val allNumeric = labels.isNotEmpty() && labels.all { it.isNotEmpty() && it.all(Char::isDigit) }
    if (allNumeric) return isIpv4Literal(host)
    return isDnsName(host)
}

private fun isIpv4Literal(host: String): Boolean {
    val parts = host.split('.')
    if (parts.size != 4) return false
    return parts.all { part ->
        part.isNotEmpty() && part.length <= 3 && part.all { it.isDigit() } &&
            (part.toIntOrNull() ?: return@all false) in 0..255
    }
}

private fun isDnsName(host: String): Boolean {
    // A name with no dot is still valid on a local network ("router"), so the
    // requirement is per-label validity rather than a mandatory TLD.
    val labels = host.split('.')
    if (labels.any { it.isEmpty() || it.length > 63 }) return false
    return labels.all { label ->
        label.first().isLetterOrDigit() && label.last().isLetterOrDigit() &&
            label.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }
}
