package com.yuroyami.pingy.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.yuroyami.pingy.GraphStyle
import com.yuroyami.pingy.PanelLayout
import com.yuroyami.pingy.PingyViewmodel
import com.yuroyami.pingy.i18n.strings
import com.yuroyami.pingy.logic.Constants
import com.yuroyami.pingy.logic.TargetParse
import com.yuroyami.pingy.logic.parseTarget
import com.yuroyami.pingy.theme.Paletting
import com.yuroyami.pingy.ui.Screen
import com.yuroyami.pingy.ui.adam.LocalViewmodel
import com.yuroyami.pingy.ui.main.components.PingGraphView
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.Font
import pingy.shared.generated.resources.Inter_Regular
import pingy.shared.generated.resources.Res

/** The neon PINGY wordmark: black outline pass under a gradient+glow pass.
 * Shared between the cockpit header and the about screen. */
@Composable
internal fun NeonWordmark(fontSize: TextUnit, fontFamily: FontFamily, modifier: Modifier = Modifier) {
    Box(modifier) {
        Text(
            text = "PINGY",
            letterSpacing = 1.sp,
            style = TextStyle(
                color = Color.Black,
                drawStyle = Stroke(miter = 0f, width = 5f, join = StrokeJoin.Round),
                fontFamily = fontFamily,
                fontSize = fontSize,
            )
        )
        Text(
            text = "PINGY",
            letterSpacing = 1.sp,
            style = TextStyle(
                brush = Brush.linearGradient(
                    colors = listOf(Paletting.A_LIGHT_COLOR, Paletting.SGN, Paletting.A_LIGHT_COLOR)
                ),
                shadow = Shadow(color = Paletting.SGN, offset = Offset(1f, 1f), blurRadius = 12f),
                fontFamily = fontFamily,
                fontSize = fontSize,
            )
        )
    }
}

/**
 * The cockpit. One dark space shared by chrome and panels: a compact neon
 * wordmark row (tap it for the about screen), a command strip whose preset
 * list morphs open OVER the panels (never pushing them), a preallocated
 * one-line notice slot, and
 * the panels in the user's chosen arrangement. Ambient auras and a vignette
 * are drawn behind everything — no images anywhere.
 */
@Composable
fun MainScreenUI() {
    val viewmodel = LocalViewmodel.current
    val s = strings

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
            // The background above is deliberately edge to edge; only content
            // is inset. safeDrawing covers status bar, navigation bar, cutout
            // and IME in one, on every platform.
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = { CockpitHeader() },
        ) { pv ->
            val layout by viewmodel.panelLayout.collectAsState()
            // Consume the whole PaddingValues. Only the top was applied
            // before, so the last panel sat under the home indicator.
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(pv)
            when (layout) {
                PanelLayout.COLUMN -> LazyColumn(modifier = contentModifier) {
                    items(viewmodel.panels, key = { it.ip }) { panel ->
                        panel.PingGraphView()
                    }
                }

                PanelLayout.GRID -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = contentModifier,
                ) {
                    items(viewmodel.panels.size, key = { viewmodel.panels[it].ip }) { index ->
                        viewmodel.panels[index].PingGraphView()
                    }
                }

                PanelLayout.PAGER -> Column(modifier = contentModifier) {
                    val pagerState = rememberPagerState { viewmodel.panels.size }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        key = { viewmodel.panels[it].ip },
                    ) { page ->
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            viewmodel.panels[page].PingGraphView()
                        }
                    }
                    if (viewmodel.panels.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            repeat(viewmodel.panels.size) { i ->
                                Box(
                                    Modifier
                                        .padding(horizontal = 3.dp)
                                        .size(6.dp)
                                        .background(
                                            color = if (pagerState.currentPage == i) Paletting.SGN
                                                    else Paletting.STRIP_BORDER,
                                            shape = CircleShape,
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CockpitHeader() {
    val viewmodel = LocalViewmodel.current
    val s = strings
    val inter = Font(Res.font.Inter_Regular)
    val interFont = remember(inter) { FontFamily(inter) }

    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
            )
            .padding(horizontal = 12.dp)
    ) {
        // Wordmark row: neon PINGY (tap for about) left, layout + style right.
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonWordmark(
                fontSize = 26.sp,
                fontFamily = interFont,
                modifier = Modifier.clickable { viewmodel.backstack.add(Screen.About) },
            )
            Spacer(Modifier.weight(1f))

            val layout by viewmodel.panelLayout.collectAsState()
            IconButton(onClick = {
                val entries = PanelLayout.entries
                val next = entries[(layout.ordinal + 1) % entries.size]
                viewmodel.panelLayout.value = next
                viewmodel.notify(
                    when (next) {
                        PanelLayout.COLUMN -> s.layoutStack
                        PanelLayout.GRID -> s.layoutGrid
                        PanelLayout.PAGER -> s.layoutPages
                    }
                )
            }) {
                Icon(
                    imageVector = when (layout) {
                        PanelLayout.COLUMN -> Icons.Filled.ViewAgenda
                        PanelLayout.GRID -> Icons.Filled.GridView
                        PanelLayout.PAGER -> Icons.Filled.ViewCarousel
                    },
                    contentDescription = s.cyclePanelLayout,
                    tint = Color(0xFF8A95A3),
                )
            }

            val style by viewmodel.graphStyle.collectAsState()
            IconButton(onClick = {
                val next = when (style) {
                    GraphStyle.PINGLETTES -> GraphStyle.MOUNTAIN_SLOPES
                    GraphStyle.MOUNTAIN_SLOPES -> GraphStyle.PINGLETTES
                }
                viewmodel.setGlobalStyle(next)
                viewmodel.notify(
                    if (next == GraphStyle.PINGLETTES) s.allPanelsBars else s.allPanelsRidge
                )
            }) {
                // The icon previews the style a tap would switch every panel to.
                Icon(
                    imageVector = if (style == GraphStyle.MOUNTAIN_SLOPES) Icons.Filled.BarChart
                                  else Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = if (style == GraphStyle.MOUNTAIN_SLOPES) {
                        s.switchAllToBars
                    } else {
                        s.switchAllToRidge
                    },
                    tint = Color(0xFF8A95A3),
                )
            }
        }

        // Command strip. The presets are not a popup: the strip itself morphs
        // open, growing downward into the list, so field and menu are one
        // continuous surface with the same width and chassis.
        // Starts empty. Pre-filling it with the first preset meant the very
        // first Add always collided with the panel the app had already made.
        val txt = remember { mutableStateOf("") }
        val presetsOpen = remember { mutableStateOf(false) }
        val addTarget: () -> Unit = {
            presetsOpen.value = false
            when (val parsed = parseTarget(txt.value)) {
                is TargetParse.Invalid -> viewmodel.notify(parsed.reason.message(s))
                is TargetParse.Valid -> when (viewmodel.addPanel(parsed.host)) {
                    PingyViewmodel.AddResult.Added -> {
                        viewmodel.notify(s.targetAdded(parsed.host))
                        txt.value = ""
                    }
                    PingyViewmodel.AddResult.Duplicate ->
                        viewmodel.notify(s.targetAlreadyMonitored(parsed.host))
                    PingyViewmodel.AddResult.AtCapacity ->
                        viewmodel.notify(s.panelLimitReached)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The strip stays fixed-height; its preset list renders in an
            // overlay Popup anchored right under it, same width and chassis,
            // so opening it never pushes the panels below. The strip's bottom
            // corners animate flat while open, morphing into the list card.
            var stripSize by remember { mutableStateOf(IntSize.Zero) }
            val bottomCorner by animateDpAsState(
                targetValue = if (presetsOpen.value) 0.dp else 26.dp,
                animationSpec = tween(200),
            )
            Box(Modifier.weight(1f)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { stripSize = it },
                    shape = RoundedCornerShape(
                        topStart = 26.dp, topEnd = 26.dp,
                        bottomStart = bottomCorner, bottomEnd = bottomCorner,
                    ),
                    color = Paletting.STRIP_BG,
                    border = BorderStroke(1.dp, Paletting.STRIP_BORDER),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = s.targetFieldDescription },
                            singleLine = true,
                            value = txt.value,
                            onValueChange = { txt.value = it },
                            label = { Text(s.targetFieldLabel, fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go,
                            ),
                            keyboardActions = KeyboardActions(onGo = { addTarget() }),
                            textStyle = TextStyle(
                                color = Paletting.SGN,
                                fontSize = 19.sp,
                                fontFamily = interFont,
                            ),
                            placeholder = {
                                Text(s.targetFieldLabel, color = Color(0xFF7C8794), fontSize = 15.sp)
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
                        val chevronAngle by animateFloatAsState(
                            targetValue = if (presetsOpen.value) 180f else 0f,
                            animationSpec = tween(220),
                        )
                        IconButton(onClick = { presetsOpen.value = !presetsOpen.value }) {
                            Icon(
                                imageVector = Icons.Filled.ExpandMore,
                                contentDescription = s.presetTargets,
                                tint = Color(0xFF7C8794),
                                modifier = Modifier.rotate(chevronAngle),
                            )
                        }
                    }
                }

                val listState = remember { MutableTransitionState(false) }
                listState.targetState = presetsOpen.value
                if (listState.currentState || listState.targetState) {
                    val density = LocalDensity.current
                    Popup(
                        alignment = Alignment.TopStart,
                        // 1dp overlap swallows the strip's bottom hairline so
                        // the two surfaces read as one continuous chassis.
                        offset = IntOffset(0, stripSize.height - with(density) { 1.dp.roundToPx() }),
                        onDismissRequest = { presetsOpen.value = false },
                        properties = PopupProperties(
                            focusable = true,
                            dismissOnBackPress = true,
                            dismissOnClickOutside = true,
                        ),
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visibleState = listState,
                            enter = expandVertically(animationSpec = tween(240)) + fadeIn(tween(180)),
                            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(120)),
                        ) {
                            Surface(
                                modifier = Modifier.width(with(density) { stripSize.width.toDp() }),
                                shape = RoundedCornerShape(
                                    topStart = 0.dp, topEnd = 0.dp,
                                    bottomStart = 26.dp, bottomEnd = 26.dp,
                                ),
                                color = Paletting.STRIP_BG,
                                border = BorderStroke(1.dp, Paletting.STRIP_BORDER),
                            ) {
                                Column(Modifier.padding(bottom = 8.dp)) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 18.dp)
                                            .height(1.dp)
                                            .background(Paletting.STRIP_BORDER.copy(alpha = 0.6f))
                                    )
                                    Constants.iplist.forEachIndexed { index, ip ->
                                        // Small stagger so the list lands top-down.
                                        val rowAlpha by animateFloatAsState(
                                            targetValue = if (presetsOpen.value) 1f else 0f,
                                            animationSpec = tween(160, delayMillis = 24 * index),
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    txt.value = ip
                                                    presetsOpen.value = false
                                                }
                                                .padding(horizontal = 20.dp, vertical = 9.dp),
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(6.dp)
                                                    .background(
                                                        Paletting.SGN.copy(alpha = 0.8f * rowAlpha),
                                                        CircleShape
                                                    )
                                            )
                                            Text(
                                                text = ip,
                                                modifier = Modifier.padding(start = 12.dp),
                                                color = Color(0xFFD8E2EC).copy(alpha = rowAlpha),
                                                fontSize = 15.sp,
                                                fontFamily = interFont,
                                            )
                                        }
                                    }
                                }
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
                        contentDescription = s.addTarget,
                        tint = Color(0xFF07130B),
                    )
                }
            }
        }

        // Preallocated notice slot: always this tall, so a message never
        // shifts the panels below. Borderless, dim, one line, self-clearing.
        val noticeVal by viewmodel.notice.collectAsState()
        LaunchedEffect(noticeVal?.id) {
            val shown = noticeVal ?: return@LaunchedEffect
            // 2.2s was below the WCAG "enough time" guidance for a message a
            // reader must notice, parse and act on.
            delay(6_000)
            if (viewmodel.notice.value?.id == shown.id) viewmodel.notice.value = null
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 3.dp, bottom = 3.dp)
                .height(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = noticeVal,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(160)) },
            ) { n ->
                if (n != null) {
                    Text(
                        text = n.text,
                        color = Color(0xFF9FE0B8),
                        fontSize = 12.sp,
                        letterSpacing = 0.3.sp,
                        fontFamily = interFont,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
