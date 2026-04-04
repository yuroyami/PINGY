package com.yuroyami.pingy.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.yuroyami.pingy.logic.Constants
import com.yuroyami.pingy.logic.PingPanel
import com.yuroyami.pingy.ui.Paletting
import com.yuroyami.pingy.ui.adam.LocalViewmodel
import com.yuroyami.pingy.ui.main.components.PingGraphView
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.imageResource
import pingy.shared.generated.resources.Inter_Regular
import pingy.shared.generated.resources.Res
import pingy.shared.generated.resources.panel_bg

val LocalPanelBackground = compositionLocalOf<ImageBitmap> { error("No panel background provided") }

@OptIn(ExperimentalLayoutApi::class)
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

    // Clean up all panels when this screen leaves composition
    DisposableEffect(null) {
        onDispose {
            viewmodel.panels.forEach { it.close() }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = Color.Gray)
                    .wrapContentHeight()
                    .systemBarsPadding(),
                shape = RoundedCornerShape(topEnd = 0.dp, topStart = 0.dp, bottomEnd = 12.dp, bottomStart = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.LightGray),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp)
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(6.dp)) {
                            Text(
                                modifier = Modifier.wrapContentWidth(),
                                text = "PINGY",
                                letterSpacing = 1.sp,
                                style = TextStyle(
                                    color = Color.Black,
                                    drawStyle = Stroke(
                                        miter = 0f,
                                        width = 5f,
                                        join = StrokeJoin.Round
                                    ),
                                    fontFamily = FontFamily(Font(Res.font.Inter_Regular)),
                                    fontSize = 24.sp,
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
                                    shadow = Shadow(
                                        color = Paletting.SGN,
                                        offset = Offset(1f, 1f),
                                        blurRadius = 10f
                                    ),
                                    fontFamily = FontFamily(Font(Res.font.Inter_Regular)),
                                    fontSize = 24.sp,
                                )
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .padding(vertical = 12.dp)
                    ) {

                        val txt = remember { mutableStateOf(Constants.iplist[0]) }

                        val digitalTxt = TextStyle(
                            color = Paletting.SGN, textAlign = TextAlign.Center,
                            fontSize = 24.sp,
                            fontFamily = FontFamily(Font(Res.font.Inter_Regular))
                        )
                        TextField(
                            modifier = Modifier.fillMaxWidth(0.65f),
                            singleLine = true,
                            value = txt.value,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.DarkGray,
                                unfocusedContainerColor = Color.DarkGray,
                                disabledContainerColor = Color.DarkGray
                            ),
                            onValueChange = { txt.value = it },
                            textStyle = digitalTxt,
                            label = {
                                Text("IP or Domain", color = Color.White)
                            })

                        Box {
                            val displayPopup = remember { mutableStateOf(false) }

                            IconButton(onClick = { displayPopup.value = !displayPopup.value }) {
                                Icon(
                                    imageVector = if (!displayPopup.value) {
                                        Icons.Filled.ExpandMore
                                    } else {
                                        Icons.Filled.ExpandLess
                                    }, ""
                                )
                            }

                            DropdownMenu(
                                expanded = displayPopup.value,
                                properties = PopupProperties(
                                    dismissOnBackPress = true,
                                    focusable = true,
                                    dismissOnClickOutside = true
                                ),
                                onDismissRequest = { displayPopup.value = !displayPopup.value }) {
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

                        FloatingActionButton(
                            modifier = Modifier.fillMaxWidth(), containerColor = Paletting.SGN,
                            onClick = {
                                if (txt.value.isNotBlank()) {
                                    for (panel in viewmodel.panels) {
                                        if (panel.ip == txt.value.trim()) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("This address is already added.")
                                            }
                                            return@FloatingActionButton
                                        }
                                    }
                                    val panel = PingPanel(ip = txt.value.trim())
                                    panel.startPinging()
                                    viewmodel.panels.add(panel)
                                    txt.value = ""
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Please enter a valid address.")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        }
                    }

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        for (panel in viewmodel.panels) {
                            ElevatedFilterChip(
                                label = {
                                    Text(
                                        text = panel.ip,
                                        fontSize = if (panel.ip.contains("www", true)) 11.sp else TextUnit.Unspecified
                                    )
                                },
                                selected = true,
                                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = true),
                                trailingIcon = { Icon(imageVector = Icons.Filled.Close, contentDescription = "") },
                                onClick = {
                                    panel.close()
                                    viewmodel.panels.remove(panel)
                                })
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    }
                }
            }
        },
        content = { pv ->
            val scrolling by remember { mutableStateOf(true) }
            CompositionLocalProvider(LocalPanelBackground provides imageResource(Res.drawable.panel_bg)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = pv.calculateTopPadding())
                        .background(color = Color.Gray),
                    userScrollEnabled = scrolling
                ) {
                    items(viewmodel.panels) { panel ->
                        panel.PingGraphView()
                    }
                }
            }
        }
    )
}
