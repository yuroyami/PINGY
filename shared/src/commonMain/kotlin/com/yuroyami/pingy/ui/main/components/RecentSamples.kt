package com.yuroyami.pingy.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.pingy.i18n.Strings
import com.yuroyami.pingy.i18n.strings
import com.yuroyami.pingy.logic.Ping
import com.yuroyami.pingy.logic.PingKind
import com.yuroyami.pingy.logic.RingBuffer
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/** How many recent samples the list offers. Enough to cover a burst, bounded. */
private const val RECENT_SAMPLE_COUNT = 20

/** How often the ages in the list are refreshed. */
private const val AGE_TICK_MS = 1_000L

/**
 * The graph's history as a list.
 *
 * Inspecting a single sample was a drag gesture on a canvas and nothing else,
 * so anyone using a keyboard or a screen reader could hear the aggregate and
 * never reach the loss burst or the spike the app exists to show them. Each row
 * is focusable and carries the whole sample in its description.
 */
@Composable
internal fun RecentSamples(pings: RingBuffer<Ping>, fontFamily: FontFamily) {
    val s = strings
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(AGE_TICK_MS)
            tick++
        }
    }

    // Read the tick so the ages below refresh with it.
    @Suppress("UNUSED_EXPRESSION") tick
    val now = TimeSource.Monotonic.markNow()
    val recent = pings.snapshot().takeLast(RECENT_SAMPLE_COUNT).asReversed()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = s.inspectSamples,
            color = StatsLabelColor,
            fontSize = 11.sp,
            fontFamily = fontFamily,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        if (recent.isEmpty()) {
            Text(
                text = s.a11yNoProbesSent,
                color = StatsDimColor,
                fontSize = 12.sp,
                fontFamily = fontFamily,
            )
            return@Column
        }
        recent.forEach { sample ->
            val ageMs = (now - sample.timestamp).inWholeMilliseconds.coerceAtLeast(0L)
            val outcome = outcomeLabel(sample, s)
            val value = sample.rttMs?.let { "${formatRtt(it)}ms" } ?: "—"
            val age = formatShortAge(ageMs)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Focusable so keyboard traversal reaches every sample, and
                    // merged so a reader speaks the whole row at once.
                    .focusable()
                    .semantics(mergeDescendants = true) {
                        contentDescription = s.sampleRow(outcome, value, age)
                    }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = outcome,
                    color = when (sample.kind) {
                        PingKind.REPLY -> readablePingTextColor(sample.value ?: 0)
                        PingKind.TIMEOUT, PingKind.LOCAL_FAULT -> FizzleColor
                        else -> StatsDimColor
                    },
                    fontSize = 12.sp,
                    fontFamily = fontFamily,
                )
                Text(
                    text = "$value  ·  $age",
                    color = SettingsTextColor,
                    fontSize = 12.sp,
                    fontFamily = fontFamily,
                )
            }
        }
    }
}

/** The localized name of what a sample turned out to be. */
internal fun outcomeLabel(sample: Ping, s: Strings): String = when (sample.kind) {
    PingKind.REPLY -> s.inspectReply
    PingKind.PENDING -> s.inspectInFlight
    PingKind.TIMEOUT -> s.inspectTimeout
    PingKind.INTERRUPTED -> s.inspectInterrupted
    PingKind.UNOBSERVED -> s.inspectUnobserved
    PingKind.LOCAL_FAULT -> sample.fault?.message(s) ?: s.inspectInterrupted
}
