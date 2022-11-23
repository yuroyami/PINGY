package app

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.BackdropScaffold
import androidx.compose.material.BackdropValue
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Addchart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.rememberBackdropScaffoldState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import app.ui.MaterialColors
import app.ui.compose.PingGraph
import app.utils.Constants
import app.utils.Panel
import kotlinx.coroutines.launch
import kotlin.properties.Delegates

class ComposeActivity : ComponentActivity() {

    /** Defines the operating PingGraph [Panel]s but in a mutable state (so it can be observed) */
    val panels = mutableStateListOf<Panel>()

    /* Screen Measurements, to be calculated later, and used in some UI functions */
    var screenWidth by Delegates.notNull<Int>()
    var screenHeight by Delegates.notNull<Int>()

    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterialApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        /** Further changing UI and Theme */
        window.statusBarColor = MaterialColors.COLOR_CYAN_700.toArgb()

        /** Decoding is heavy, we do it once and pass it to composables */
        val panelBG = BitmapFactory.decodeResource(resources, R.drawable.panel_bg).asImageBitmap()

        /** First main panel (unremovable) */
        panels.add(Panel(ip = "1.1.1.1", lifecycleScope))

        /** Starting our Jetpack Compose visual symphony, let's conduct ! */
        setContent {
            /** Getting screen dimensions */
            val configuration = LocalConfiguration.current
            val density = LocalDensity.current
            screenHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() }
            screenWidth = with(density) { configuration.screenHeightDp.dp.roundToPx() }

            val scaffoldState = rememberBackdropScaffoldState(BackdropValue.Revealed)
            val scaffoldMaxHeight = remember { mutableStateOf(48f) }
            val scope = rememberCoroutineScope()

            /** Doing 'remember' on a mutable state means that the system will recompose
             * any view that is using this rememberable */
            val panelsRememberable = remember { panels }

            BackdropScaffold(
                backLayerContent = {
                    Column(modifier = Modifier.fillMaxHeight()) {
                        Spacer(modifier = Modifier.height(6.dp))

                        for (panel in panelsRememberable) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned {
                                        scaffoldMaxHeight.value = with(density) { it.size.height.toDp().value + 18f }
                                    }) {

                                Text(text = panels.indexOf(panel).toString(), fontSize = 18.sp, color = Color.DarkGray)

                                Spacer(modifier = Modifier.width(8.dp))

                                val txt = remember { mutableStateOf("") }

                                OutlinedTextField(
                                    modifier = Modifier.fillMaxWidth(0.55f),
                                    value = txt.value,
                                    onValueChange = { txt.value = it },
                                    label = { Text("Host N°${panels.indexOf(panel)}") }
                                )

                                Box() {
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

                                    DropdownMenu(expanded = displayPopup.value,
                                        properties = PopupProperties(
                                            dismissOnBackPress = true,
                                            focusable = true,
                                            dismissOnClickOutside = true
                                        ),
                                        onDismissRequest = { displayPopup.value = !displayPopup.value }) {
                                        Constants.iplist.forEach { ip ->
                                            DropdownMenuItem(onClick = { }) {
                                                Text(ip)
                                            }
                                        }
                                    }
                                }

                                if (panels.indexOf(panel) != 0) {
                                    IconButton(onClick = { panelsRememberable.remove(panel) }) {
                                        Icon(imageVector = Icons.Filled.Close, "")
                                    }
                                } else {
                                    FloatingActionButton(onClick = {
                                        panels.add(Panel(ip = Constants.iplist.random(), lifecycleScope))
                                        scope.launch { scaffoldState.conceal() }
                                    }) {
                                        Icon(Icons.Filled.Addchart, contentDescription = null)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                        }

                    }
                },
                frontLayerContent = {
                    Column(modifier = Modifier.fillMaxHeight()) {
                        for (panel in panelsRememberable) {
                            Row() {
                                PingGraph.PingGraphView(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .padding(20.dp),
                                    panel, panelBG
                                )
                            }
                        }
                    }

                },
                frontLayerBackgroundColor = MaterialColors.COLOR_CYAN_200,
                backLayerBackgroundColor = MaterialColors.COLOR_CYAN_700,
                peekHeight = scaffoldMaxHeight.value.dp, headerHeight = 48.dp, scaffoldState = scaffoldState, appBar = {},
            )
        }
    }

}

