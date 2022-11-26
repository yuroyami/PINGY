package app

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumedWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Addchart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.ui.compose.PingGraph
import app.utils.Constants
import app.utils.Panel
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.flowlayout.MainAxisAlignment
import kotlinx.coroutines.launch
import kotlin.properties.Delegates

class ComposeActivity : ComponentActivity() {

    /** Defines the operating PingGraph [Panel]s but in a mutable state (so it can be observed) */
    val panels = mutableStateListOf<Panel>()

    /* Screen Measurements, to be calculated later, and used in some UI functions */
    var screenWidth by Delegates.notNull<Int>()
    var screenHeight by Delegates.notNull<Int>()

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        /** Further changing UI and Theme */
        window.statusBarColor = Color.LightGray.toArgb() //Paletting.B_DARK_COLOR.toArgb()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        /** Decoding is heavy, we do it once and pass it to composables */
        val panelBG = BitmapFactory.decodeResource(resources, R.drawable.panel_bg).asImageBitmap()

        /** First main panel (unremovable) */
        panels.add(Panel(ip = "1.1.1.1"))

        /** Starting our Jetpack Compose visual symphony, let's conduct ! */
        setContent {
            /** Getting screen properties */
            val configuration = LocalConfiguration.current
            val density = LocalDensity.current
            screenHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() }
            screenWidth = with(density) { configuration.screenHeightDp.dp.roundToPx() }

            /** Remembering stuff like scope for onClicks, snackBar host state for snackbars ... etc */
            val scope = rememberCoroutineScope()
            val snackbarHostState = remember { SnackbarHostState() }

            /** Doing 'remember' on a mutable state means that the system will recompose
             * any view that is using this rememberable */
            val panelsRememberable = remember { panels }

            /** Colors */
            val color1 = Color(resources.getColor(R.color.sgn_shade))

            /** Starting the UI composition with a scaffold that lays everything out */
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                        /** COLUMN acts like a linear layout with vertical orientation */
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(color = Color.Gray)
                                .wrapContentHeight(),
                            shape = RoundedCornerShape(topEnd = 0.dp, topStart = 0.dp, bottomEnd = 12.dp, bottomStart = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.LightGray),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp),
                        ) {

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {

                                /** ROW acts like a linear layout with horizontal orientation */
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth(0.95f)
                                        .padding(vertical = 12.dp)
                                ) {

                                    /** Input text field with custom style */
                                    val txt = remember { mutableStateOf(Constants.iplist[0]) }

                                    val digitalTxt = TextStyle(
                                        color = color1, textAlign = TextAlign.Center,
                                        fontSize = 24.sp, fontFamily = FontFamily(Font(R.font.digital))
                                    )
                                    TextField(
                                        modifier = Modifier.fillMaxWidth(0.65f),
                                        singleLine = true,
                                        value = txt.value,
                                        colors = TextFieldDefaults.textFieldColors(containerColor = Color.DarkGray),
                                        onValueChange = { txt.value = it },
                                        textStyle = digitalTxt,
                                        label = {
                                            Text("IP or Domain", color = Color.White)
                                        })

                                    /** Box that overlaps button and its popup menu */
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
                                                DropdownMenuItem(text = { Text(ip) },
                                                    onClick = {
                                                        txt.value = ip
                                                        displayPopup.value = false
                                                    })
                                            }
                                        }
                                    }

                                    /** The button that adds panels, I like it being a FAB for the style */
                                    FloatingActionButton(modifier = Modifier.fillMaxWidth(), containerColor = color1,
                                        onClick = {
                                            if (txt.value.isNotBlank()) {
                                                for (panel in panelsRememberable) {
                                                    if (panel.ip == txt.value.trim()) {
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar("This address is already added.")
                                                        }
                                                        return@FloatingActionButton
                                                    }
                                                }
                                                panels.add(Panel(ip = txt.value.trim()))
                                                txt.value = ""
                                            } else {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Please enter a valid address.")
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Filled.Addchart, contentDescription = null)
                                    }
                                }

                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp), mainAxisAlignment = MainAxisAlignment.Center
                                ) {
                                    for (panel in panelsRememberable) {
                                        ElevatedFilterChip(
                                            label = {
                                                Text(
                                                    text = panel.ip,
                                                    fontSize = if (panel.ip.contains("www", true)) 11.sp else TextUnit.Unspecified
                                                )
                                            },
                                            selected = true,
                                            border = FilterChipDefaults.filterChipBorder(),
                                            trailingIcon = { Icon(imageVector = Icons.Filled.Close, contentDescription = "") },
                                            onClick = {
                                                panelsRememberable.remove(panel)
                                            })
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                            }
                        }
                    }
                },
                content = {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(it)
                            .background(color = Color.Gray)
                            .consumedWindowInsets(it)
                    ) {
                        for (panel in panelsRememberable) {
                            Row(modifier = Modifier.onGloballyPositioned {
                                //scaffoldMaxHeight.value = with(density) { (it.size.height.toDp().value + 18f) * panelsRememberable.size }
                            }) {
                                PingGraph.PingGraphView(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .padding(20.dp),
                                    panel,
                                    panelBG
                                )
                            }
                        }
                    }

                },

                )
        }
    }

}

