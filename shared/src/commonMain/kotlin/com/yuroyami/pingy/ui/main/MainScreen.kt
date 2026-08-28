package com.yuroyami.pingy.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.yuroyami.pingy.GraphStyle
import com.yuroyami.pingy.logic.Constants
import com.yuroyami.pingy.logic.PingPanel
import com.yuroyami.pingy.theme.Paletting
import com.yuroyami.pingy.theme.pingColor
import com.yuroyami.pingy.ui.adam.LocalViewmodel
import com.yuroyami.pingy.ui.main.components.PingGraphView
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import pingy.shared.generated.resources.Inter_Regular
import pingy.shared.generated.resources.Res

/**
 * Normalize whatever the user pasted into the "IP or Domain" field into the
 * string we actually pass to the resolver. Strips scheme ("https://"),
 * trailing paths ("/foo") and surrounding whitespace. Returns `null` when
 * nothing usable remains.
 */
private fun sanitizeHost(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val noScheme = trimmed
        .removePrefix("https://").removePrefix("http://")
        .removePrefix("HTTPS://").removePrefix("HTTP://")
    val pathStripped = noScheme.substringBefore('/').substringBefore('?')
    return pathStripped.ifBlank { null }
}

/**
 * The cockpit. One dark space shared by chrome and panels: a compact neon
 * wordmark row, a single slim command strip to add targets, and live target
 * chips whose glowing dots carry each panel's current ping color. Ambient
 * auras and a vignette are drawn behind everything — no images anywhere.
 */
@Composable
fun MainScreenUI() {
    val viewmodel = LocalViewmodel.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(null) {
        if (viewmodel.panels.isEmpty()) {
            val panel = PingPanel(ip = "1.1.1.1")
            panel.startPinging()
            viewmodel.panels.add(panel)
        }
    }
    // Panel lifecycle ownership lives on the ViewModel: PingyViewmodel.onCleared()
    // calls close() on every panel when the VM itself is disposed.

    Box(
        Modifier
            .fillMaxSize()
            .background(Paletting.COCKPIT_BG)
            .drawBehind {
                // One whisper of brand green behind the header and a soft
                // vignette; the chrome stays neutral so data owns the color.
                drawRect(
                    brush = Brush.radialGradient(
                        0f to Paletting.SGN.copy(alpha = 0.04f),
                        1f to Color.Transparent,
                        center = Offset(size.width * 0.16f, size.height * 0.10f),
                        radius = size.width * 0.55f,
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        0f to Color.Transparent,
                        1f to Color(0x66000000),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.height * 0.75f,
                    )
                )
            }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = { CockpitHeader(snackbarHostState) },
        ) { pv ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = pv.calculateTopPadding()),
            ) {
                items(viewmodel.panels) { panel ->
                    panel.PingGraphView()
                }
            }
        }
    }
}

@Composable
private fun CockpitHeader(snackbarHostState: SnackbarHostState) {
    val viewmodel = LocalViewmodel.current
    val scope = rememberCoroutineScope()
    val interFont = FontFamily(Font(Res.font.Inter_Regular))

    Column(
        Modifier
            .fillMaxWidth()
            .systemBarsPadding()
            .padding(horizontal = 12.dp)
    ) {
        // Wordmark row: neon PINGY left, artistic-direction toggle right.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Text(
                    modifier = Modifier.wrapContentWidth(),
                    text = "PINGY",
                    letterSpacing = 1.sp,
                    style = TextStyle(
                        color = Color.Black,
                        drawStyle = Stroke(miter = 0f, width = 5f, join = StrokeJoin.Round),
                        fontFamily = interFont,
                        fontSize = 26.sp,
                    )
                )
                Text(
                    modifier = Modifier.wrapContentWidth(),
                    text = "PINGY",
                    letterSpacing = 1.sp,
                    style = TextStyle(
                        brush = Brush.linearGradient(
                            colors = listOf(Paletting.A_LIGHT_COLOR, Paletting.SGN, Paletting.A_LIGHT_COLOR)
                        ),
                        shadow = Shadow(color = Paletting.SGN, offset = Offset(1f, 1f), blurRadius = 12f),
                        fontFamily = interFont,
                        fontSize = 26.sp,
                    )
                )
            }
            Spacer(Modifier.weight(1f))
            val style by viewmodel.graphStyle.collectAsState()
            IconButton(onClick = {
                viewmodel.graphStyle.value = when (style) {
                    GraphStyle.PINGLETTES -> GraphStyle.MOUNTAIN_SLOPES
                    GraphStyle.MOUNTAIN_SLOPES -> GraphStyle.PINGLETTES
                }
            }) {
                Icon(
                    imageVector = if (style == GraphStyle.PINGLETTES) Icons.Filled.Equalizer
                                  else Icons.Filled.Terrain,
                    contentDescription = if (style == GraphStyle.PINGLETTES) {
                        "Switch to mountain-slopes graph style"
                    } else {
                        "Switch to pinglettes graph style"
                    },
                    tint = Color(0xFF8A95A3),
                )
            }
        }

        // Command strip: input + preset chevron + glowing add, one slim row.
        val txt = remember { mutableStateOf(Constants.iplist[0]) }
        val addTarget: () -> Unit = {
            val host = sanitizeHost(txt.value)
            when {
                host == null -> scope.launch {
                    snackbarHostState.showSnackbar("Please enter a valid address.")
                }
                viewmodel.panels.any { it.ip == host } -> scope.launch {
                    snackbarHostState.showSnackbar("This address is already added.")
                }
                else -> {
                    val panel = PingPanel(ip = host)
                    panel.startPinging()
                    viewmodel.panels.add(panel)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(26.dp),
                color = Paletting.STRIP_BG,
                border = androidx.compose.foundation.BorderStroke(1.dp, Paletting.STRIP_BORDER),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        value = txt.value,
                        onValueChange = { txt.value = it },
                        textStyle = TextStyle(
                            color = Paletting.SGN,
                            fontSize = 19.sp,
                            fontFamily = interFont,
                        ),
                        placeholder = {
                            Text("IP or domain", color = Color(0xFF5E6874), fontSize = 15.sp)
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Paletting.SGN,
                        ),
                    )
                    Box {
                        val displayPopup = remember { mutableStateOf(false) }
                        IconButton(onClick = { displayPopup.value = !displayPopup.value }) {
                            Icon(
                                imageVector = if (!displayPopup.value) Icons.Filled.ExpandMore
                                              else Icons.Filled.ExpandLess,
                                contentDescription = "Preset targets",
                                tint = Color(0xFF7C8794),
                            )
                        }
                        DropdownMenu(
                            expanded = displayPopup.value,
                            properties = PopupProperties(
                                dismissOnBackPress = true,
                                focusable = true,
                                dismissOnClickOutside = true
                            ),
                            onDismissRequest = { displayPopup.value = !displayPopup.value },
                        ) {
                            Constants.iplist.forEach { ip ->
                                DropdownMenuItem(
                                    text = { Text(ip) },
                                    onClick = {
                                        txt.value = ip
                                        displayPopup.value = false
                                    })
                            }
                        }
                    }
                }
            }
            Surface(
                onClick = addTarget,
                modifier = Modifier.padding(start = 10.dp).size(52.dp),
                shape = CircleShape,
                color = Paletting.SGN,
                shadowElevation = 10.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Add target",
                        tint = Color(0xFF07130B),
                    )
                }
            }
        }

        // Live target chips: each dot glows in its panel's current ping color.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (panel in viewmodel.panels) {
                LiveTargetChip(
                    panel = panel,
                    fontFamily = interFont,
                    onRemove = {
                        panel.close()
                        viewmodel.panels.remove(panel)
                    },
                )
            }
        }
    }
}

/** A target pill whose dot and border tint follow the panel's latest result live. */
@Composable
private fun LiveTargetChip(
    panel: PingPanel,
    fontFamily: FontFamily,
    onRemove: () -> Unit,
) {
    val version by panel.pingVersion.collectAsState()
    val latest = remember(version) { panel.pings.last() }
    val dotColor = when {
        latest == null -> Color(0xFF5A6470)
        latest.value == null || latest.value < 0 -> Paletting.LOST_RED
        else -> pingColor(latest.value)
    }
    Surface(
        shape = CircleShape,
        color = Paletting.CHIP_BG,
        border = androidx.compose.foundation.BorderStroke(1.dp, Paletting.STRIP_BORDER),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .background(dotColor, CircleShape)
                    .border(2.dp, dotColor.copy(alpha = 0.25f), CircleShape)
            )
            Text(
                text = panel.ip,
                modifier = Modifier.padding(start = 8.dp),
                color = Color(0xFFD8E2EC),
                fontSize = 13.sp,
                fontFamily = fontFamily,
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove ${panel.ip}",
                    tint = Color(0xFF7C8794),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
