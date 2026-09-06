package com.yuroyami.pingy.ui.main.components

import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yuroyami.pingy.i18n.strings
import com.yuroyami.pingy.logic.PingPanel
import com.yuroyami.pingy.theme.Paletting
import com.yuroyami.pingy.ui.adam.LocalViewmodel
import kotlin.math.roundToInt

/**
 * The two panels below the graph: the statistics strip and the settings sheet.
 *
 * Separated from the drawing because they share nothing with it but the data
 * class, and they were the largest block of non-canvas code in the file.
 */

/**
 * Windowed instrument strip on ONE line, always. The whole strip is a single
 * annotated string with relative (em) span sizes, and auto-size shrinks the
 * base until it fits the panel's width, so a half-width grid cell just
 * renders the same line smaller instead of wrapping. Every number describes
 * the SAME slice of time the canvas shows.
 */
@Composable
internal fun StatsSheet(stats: WindowStats, fontFamily: FontFamily) {
    val s = strings
    var helpOpen by remember { mutableStateOf(false) }
    val line = buildAnnotatedString {
        fun label(text: String) {
            withStyle(
                SpanStyle(
                    color = StatsLabelColor,
                    fontSize = 0.72.em,
                    letterSpacing = 0.09.em,
                    fontWeight = FontWeight.Medium,
                )
            ) { append(text) }
        }

        fun value(text: String, color: Color, glow: Boolean) {
            withStyle(
                SpanStyle(
                    color = color,
                    fontWeight = FontWeight.Bold,
                    shadow = if (glow) Shadow(color = color, blurRadius = 12f) else null,
                )
            ) { append(text) }
        }

        label(s.statAverage + " ")
        value(
            stats.avg?.let { "${formatRtt(it)}ms" } ?: "—",
            stats.avg?.let { calcPingColor(it.roundToInt()) } ?: StatsDimColor,
            stats.avg != null,
        )
        // Mean absolute successive difference between consecutive replies.
        // Labelled JIT rather than "±", because a plus-minus sign promises a
        // symmetric interval and this is not one. RTT stays unrounded until
        // after the statistics run, so sub-millisecond values survive instead
        // of flooring to zero.
        label("  " + s.statJitter + " ")
        value(stats.jitter?.let { formatRtt(it) } ?: "—", SettingsTextColor, false)
        label("  " + s.statLoss + " ")
        run {
            val lossPercent = if (stats.count > 0) stats.lost * 100f / stats.count else null
            // Loss only earns color once packets actually die.
            val color = when {
                lossPercent == null -> StatsDimColor
                lossPercent <= 0.001f -> SettingsTextColor
                else -> lossColor(lossPercent)
            }
            value(
                lossPercent?.let { "${formatFloat1(it)}% (${stats.lost}/${stats.count})" } ?: "—",
                color,
                lossPercent != null && lossPercent > 0.001f,
            )
        }
        label("  " + s.statGone + " ")
        run {
            val gone = stats.gonePct
            // Time-based loss: how much of the window was actually dark.
            value(
                gone?.let { "${formatFloat1(it)}%" } ?: "—",
                when {
                    gone == null -> StatsDimColor
                    gone <= 0.05f -> SettingsTextColor
                    else -> lossColor(gone)
                },
                gone != null && gone > 0.05f,
            )
        }
        label("  " + s.statRange + " ")
        value(
            // Format, do not interpolate: these are Doubles now, and printing
            // one raw gives "35.567–26866.845ms".
            if (stats.min != null && stats.max != null) {
                "${formatRtt(stats.min)}–${formatRtt(stats.max)}ms"
            } else "—",
            SettingsTextColor,
            false,
        )
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = line,
            // Wrapping to a second line beats shrinking to 6sp, which is what
            // the old single-line fit did in a narrow cell or at a large text
            // setting: asking for bigger text produced smaller text.
            maxLines = 2,
            softWrap = true,
            style = TextStyle(fontFamily = fontFamily, fontSize = 15.sp, color = SettingsTextColor),
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 15.sp, stepSize = 0.25.sp),
        )
    }

    // The strip is four abbreviations and a span. Without this the reader has
    // to guess what each one measures, so the explanation sits one tap away.
    Text(
        text = s.whatDoTheseMean,
        color = StatsLabelColor,
        fontSize = 11.sp,
        fontFamily = fontFamily,
        modifier = Modifier
            .clickable(role = Role.Button) { helpOpen = !helpOpen }
            .padding(vertical = 12.dp, horizontal = 4.dp),
    )
    if (helpOpen) StatsHelp(fontFamily)
    }
}

/** Plain-language explanation of each figure in the strip. */
@Composable
private fun StatsHelp(fontFamily: FontFamily) {
    val s = strings
    Column(Modifier.padding(bottom = 8.dp)) {
        listOf(s.helpAverage, s.helpJitter, s.helpLoss, s.helpGone).forEach { line ->
            Text(
                text = line,
                color = SettingsTextColor,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontFamily = fontFamily,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}

/**
 * Settings deck: one slim row per dial (label, slider, live value), plus a
 * persist switch. Bound directly to the panel's StateFlows; everything
 * applies on-the-fly.
 */
@Composable
internal fun PingPanel.SettingsSheet(fontFamily: FontFamily) {
    val s = strings
    val viewmodel = LocalViewmodel.current
    val packetSizeVal by packetSize.collectAsState()
    val intervalVal by interval.collectAsState()
    val roofVal by roof.collectAsState()
    val angleOfAttackVal by angleOfAttack.collectAsState()
    val timeframeMsVal by timeframeMs.collectAsState()
    val canvasHeightFractionVal by canvasHeightFraction.collectAsState()
    val persistVal by persistAcrossSessions.collectAsState()
    val resolvedVal by resolvedAddress.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                // Which address the measurements actually came from. A name can
                // point somewhere new without anything else on screen changing.
                text = resolvedVal?.takeIf { it != ip }?.let { "$ip ($it)" } ?: ip,
                color = StatsSubColor,
                fontSize = 10.sp,
                fontFamily = fontFamily,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = s.remember,
                color = StatsLabelColor,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
                fontFamily = fontFamily,
            )
            Switch(
                modifier = Modifier
                    .scale(0.62f)
                    .semantics { contentDescription = s.remember },
                checked = persistVal,
                onCheckedChange = { checked ->
                    persistAcrossSessions.value = checked
                    viewmodel.notify(
                        if (checked) s.willReturnNextLaunch(ip)
                        else s.willNotReturnNextLaunch(ip)
                    )
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Paletting.SGN2),
            )
        }

        CompactSlider(
            label = s.settingInterval,
            valueText = if (intervalVal <= 0L) s.intervalAdaptive else "${intervalVal}ms",
            value = intervalVal.toFloat(),
            range = 0f..2000f,
            steps = 39, // 50ms steps; 0 = fire-as-fast-as-replies-land
            fontFamily = fontFamily,
        ) { interval.value = it.toLong() }
        CompactSlider(
            label = s.settingPacket,
            valueText = "${packetSizeVal} B",
            value = packetSizeVal.toFloat(),
            range = 16f..480f,
            steps = 28, // 16-byte steps
            fontFamily = fontFamily,
        ) { packetSize.value = it.toInt() }
        CompactSlider(
            label = s.settingAttack,
            valueText = if (angleOfAttackVal <= 0.01f) s.valueLinear else formatFloat1(angleOfAttackVal),
            value = angleOfAttackVal,
            range = 0f..20f,
            steps = 40,
            fontFamily = fontFamily,
        ) { angleOfAttack.value = it }
        CompactSlider(
            label = s.settingWindow,
            valueText = formatTimeframe(timeframeMsVal),
            value = timeframeMsVal.toFloat(),
            range = 1_000f..30_000f,
            steps = 28, // 1s steps
            fontFamily = fontFamily,
        ) { timeframeMs.value = it.toLong() }
        CompactSlider(
            label = s.settingRoof,
            valueText = "${roofVal}ms",
            value = roofVal.toFloat(),
            range = 100f..2000f,
            steps = 18, // 100ms steps
            fontFamily = fontFamily,
        ) { roof.value = it.toInt() }
        // Reset needs a control that can be seen and read out. A long press on
        // the graph alone is invisible to a screen reader and hints at nothing.
        Text(
            text = s.resetPanelSettings,
            color = Paletting.SGN,
            fontSize = 12.sp,
            fontFamily = fontFamily,
            modifier = Modifier
                .padding(top = 6.dp)
                .clickable(role = Role.Button) {
                    val before = preferenceSnapshot()
                    resetPreferences()
                    viewmodel.notify(
                        text = s.settingsWereReset,
                        actionLabel = s.undo,
                        action = { restorePreferences(before) },
                    )
                }
                .padding(vertical = 14.dp, horizontal = 4.dp),
        )

        CompactSlider(
            label = s.settingHeight,
            valueText = "${(canvasHeightFractionVal * 100).roundToInt()}%",
            value = canvasHeightFractionVal,
            range = 0.10f..0.45f,
            steps = 34,
            fontFamily = fontFamily,
        ) { canvasHeightFraction.value = it }
    }
}

/** One settings row: fixed label, elastic slider, fixed live value. */
@Composable
internal fun CompactSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    fontFamily: FontFamily,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 36dp is fine visually but too short to hit reliably; the extra
            // height is padding, so the row still looks the same.
            .heightIn(min = 48.dp)
            // The label and the value sit in separate Text nodes, so a screen
            // reader announced a bare "slider" with no name and no units.
            // Merging them gives it both.
            .semantics(mergeDescendants = true) {
                contentDescription = "$label, $valueText"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Proportional rather than fixed: a translated label or a large text
        // setting overflowed a 58dp column and clipped mid-word.
        Text(
            text = label,
            color = StatsLabelColor,
            fontSize = 11.sp,
            fontFamily = fontFamily,
            maxLines = 2,
            modifier = Modifier.weight(0.3f),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Paletting.A_MAIN_COLOR,
                activeTrackColor = Paletting.A_MAIN_COLOR,
                inactiveTrackColor = Paletting.A_MAIN_COLOR.copy(alpha = 0.25f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Text(
            text = valueText,
            color = SettingsTextColor,
            fontSize = 11.sp,
            fontFamily = fontFamily,
            maxLines = 2,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.3f),
        )
    }
}
