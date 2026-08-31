package com.yuroyami.pingy.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kitessot.generated.BuildConfig
import com.yuroyami.pingy.i18n.strings
import com.yuroyami.pingy.theme.Paletting
import com.yuroyami.pingy.ui.adam.LocalViewmodel
import org.jetbrains.compose.resources.Font
import pingy.shared.generated.resources.Inter_Regular
import pingy.shared.generated.resources.Res

private const val REPO = "https://github.com/yuroyami/PINGY"

/**
 * Legal and privacy.
 *
 * Reachable from About in two taps. The app ships under the AGPL and its store
 * listing has to point somewhere for privacy; neither had any route inside the
 * app before, which is both a licence obligation and a store requirement.
 */
@Composable
fun LegalScreenUI() {
    val viewmodel = LocalViewmodel.current
    val s = strings
    val uriHandler = LocalUriHandler.current
    val inter = Font(Res.font.Inter_Regular)
    val interFont = FontFamily(inter)

    Column(
        Modifier
            .fillMaxSize()
            .background(Paletting.COCKPIT_BG)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        IconButton(
            onClick = {
                if (viewmodel.backstack.size > 1) {
                    viewmodel.backstack.removeAt(viewmodel.backstack.lastIndex)
                }
            },
            modifier = Modifier.padding(bottom = 4.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = s.back,
                tint = Color(0xFF8A95A3),
            )
        }

        Text(
            text = s.legalTitle,
            color = Color(0xFFD8E2EC),
            fontSize = 24.sp,
            fontFamily = interFont,
            modifier = Modifier.padding(bottom = 18.dp),
        )

        LegalRow(s.privacyPolicy, interFont) { uriHandler.openUri("$REPO/blob/master/PRIVACY_POLICY.md") }
        LegalRow(s.licenseAndNotices, interFont) { uriHandler.openUri("$REPO/blob/master/LICENSE") }
        LegalRow(s.openSourceLicenses, interFont) { uriHandler.openUri("$REPO/blob/master/THIRD_PARTY_NOTICES.md") }
        LegalRow(s.sourceCode, interFont) { uriHandler.openUri(REPO) }

        Text(
            text = "Pingy ${BuildConfig.versionName} · AGPL-3.0",
            color = Color(0xFF7C8794),
            fontSize = 12.sp,
            fontFamily = interFont,
            modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun LegalRow(label: String, fontFamily: FontFamily, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            // 56dp keeps the row comfortably above the 48dp minimum target.
            .padding(vertical = 4.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .background(Paletting.STRIP_BG, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = Color(0xFFC9D2DD), fontSize = 15.sp, fontFamily = fontFamily)
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = Color(0xFF7C8794),
        )
    }
}
