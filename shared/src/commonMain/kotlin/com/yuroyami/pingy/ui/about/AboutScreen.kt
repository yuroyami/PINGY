package com.yuroyami.pingy.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.pingy.theme.Paletting
import com.yuroyami.pingy.ui.adam.LocalViewmodel
import com.yuroyami.pingy.ui.main.NeonWordmark
import com.yuroyami.pingy.i18n.strings
import com.yuroyami.pingy.ui.Screen
import kitessot.generated.BuildConfig
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import pingy.shared.generated.resources.Inter_Regular
import pingy.shared.generated.resources.Res
import pingy.shared.generated.resources.pingy_vector

/** A propos. The logo's icon and label sit side by side (the mark itself
 * stacks them vertically, so here the vector plays icon and the neon
 * wordmark plays label). */
@Composable
fun AboutScreenUI() {
    val s = strings
    val viewmodel = LocalViewmodel.current
    val inter = Font(Res.font.Inter_Regular)
    val interFont = remember(inter) { FontFamily(inter) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Paletting.COCKPIT_BG)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        0f to Paletting.SGN.copy(alpha = 0.05f),
                        1f to Color.Transparent,
                        center = Offset(size.width * 0.5f, size.height * 0.32f),
                        radius = size.width * 0.8f,
                    )
                )
            }
    ) {
        IconButton(
            modifier = Modifier
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(4.dp),
            onClick = {
                if (viewmodel.backstack.size > 1) {
                    viewmodel.backstack.removeAt(viewmodel.backstack.lastIndex)
                }
            },
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = s.back,
                tint = Color(0xFF8A95A3),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(Res.drawable.pingy_vector),
                    contentDescription = null,
                    modifier = Modifier.size(88.dp),
                )
                Spacer(Modifier.size(16.dp))
                NeonWordmark(fontSize = 40.sp, fontFamily = interFont)
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "v${BuildConfig.versionName}",
                color = Color(0xFF7C8794),
                fontSize = 14.sp,
                fontFamily = interFont,
            )
            Spacer(Modifier.height(26.dp))
            Text(
                text = s.aboutTagline,
                color = Color(0xFFC9D2DD),
                fontSize = 14.sp,
                fontFamily = interFont,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = s.aboutTech,
                color = Color(0xFF5E6874),
                fontSize = 12.sp,
                fontFamily = interFont,
            )

            Spacer(Modifier.height(30.dp))
            // Store policy expects a privacy route inside the app, and an AGPL
            // binary has to carry its licence with it. Both live one tap away.
            Text(
                text = s.legalTitle,
                color = Paletting.SGN,
                fontSize = 15.sp,
                fontFamily = interFont,
                modifier = Modifier
                    .clickable(role = Role.Button) { viewmodel.backstack.add(Screen.Legal) }
                    .padding(vertical = 14.dp, horizontal = 24.dp),
            )
        }
    }
}
