package com.yuroyami.pingy.logic

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yuroyami.pingy.utils.loggye
import com.yuroyami.pingy.utils.pingyDataStoreDir
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath

/** Everything a panel needs to be reborn on the next launch. */
@Serializable
data class PanelSpec(
    val ip: String,
    val intervalMs: Long = PingPanel.DEFAULT_INTERVAL_MS,
    val packetSize: Int = PingPanel.DEFAULT_PACKET_SIZE,
    val roof: Int = PingPanel.DEFAULT_ROOF,
    val angleOfAttack: Float = PingPanel.DEFAULT_ANGLE_OF_ATTACK,
    val timeframeMs: Long = PingPanel.DEFAULT_TIMEFRAME_MS,
    val canvasHeightFraction: Float = PingPanel.DEFAULT_CANVAS_HEIGHT_FRACTION,
    val style: String? = null,
)

/** What Pingy remembers across sessions. */
data class PersistedState(
    val panels: List<PanelSpec>,
    val graphStyle: String?,
    val panelLayout: String?,
)

/** Preferences DataStore wrapper. One instance per process (DataStore refuses
 * two actives on the same file), created lazily on first use. */
object PingyStore {
    private val json = Json { ignoreUnknownKeys = true }

    private val KEY_PANELS = stringPreferencesKey("panels")
    private val KEY_STYLE = stringPreferencesKey("graph_style")
    private val KEY_LAYOUT = stringPreferencesKey("panel_layout")

    private val store: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.createWithPath {
            "${pingyDataStoreDir()}/pingy.preferences_pb".toPath()
        }
    }

    suspend fun load(): PersistedState = runCatching {
        val prefs = store.data.first()
        PersistedState(
            panels = prefs[KEY_PANELS]?.let { json.decodeFromString<List<PanelSpec>>(it) } ?: emptyList(),
            graphStyle = prefs[KEY_STYLE],
            panelLayout = prefs[KEY_LAYOUT],
        )
    }.getOrElse { e ->
        loggye("PingyStore: load failed, starting fresh", e)
        PersistedState(emptyList(), null, null)
    }

    suspend fun save(panels: List<PanelSpec>, graphStyle: String, panelLayout: String) {
        runCatching {
            store.edit { prefs ->
                prefs[KEY_PANELS] = json.encodeToString(panels)
                prefs[KEY_STYLE] = graphStyle
                prefs[KEY_LAYOUT] = panelLayout
            }
        }.getOrElse { e -> loggye("PingyStore: save failed", e) }
    }
}
