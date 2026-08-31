package com.yuroyami.pingy.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.pingy.i18n.strings
import com.yuroyami.pingy.theme.Paletting

/**
 * The states the cockpit can be in when it is not showing panels.
 *
 * Four situations look identical if you only draw an empty screen: a first
 * run, a cockpit the user emptied on purpose, a store that could not be read,
 * and the moment before the store has been read at all. Each gets its own
 * screen here, so a problem never passes for an empty list.
 */

@Composable
internal fun CockpitLoading(fontFamily: FontFamily) {
    CenteredState(
        title = strings.loading,
        body = null,
        fontFamily = fontFamily,
        leading = { CircularProgressIndicator(color = Paletting.SGN, strokeWidth = 2.dp, modifier = Modifier.size(22.dp)) },
    )
}

@Composable
internal fun CockpitFirstRun(fontFamily: FontFamily) {
    val s = strings
    CenteredState(title = s.firstRunTitle, body = s.firstRunBody, fontFamily = fontFamily)
}

@Composable
internal fun CockpitEmpty(fontFamily: FontFamily) {
    val s = strings
    CenteredState(title = s.emptyTitle, body = s.emptyBody, fontFamily = fontFamily)
}

@Composable
internal fun CockpitLoadFailed(fontFamily: FontFamily) {
    val s = strings
    // Deliberately says nothing was changed: the store is left untouched on a
    // failed read, and a person needs to know their data is still there.
    CenteredState(title = s.storeUnreadable, body = null, fontFamily = fontFamily, danger = true)
}

@Composable
private fun CenteredState(
    title: String,
    body: String?,
    fontFamily: FontFamily,
    danger: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
) {
    val spoken = if (body != null) "$title. $body" else title
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        leading?.let {
            it()
            androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
        }
        Text(
            text = title,
            color = if (danger) Color(0xFFFF8A80) else Color(0xFFC9D2DD),
            fontSize = 16.sp,
            fontFamily = fontFamily,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                text = body,
                color = Color(0xFF8A95A3),
                fontSize = 13.sp,
                fontFamily = fontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
