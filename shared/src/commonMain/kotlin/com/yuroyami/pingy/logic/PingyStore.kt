package com.yuroyami.pingy.logic

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.yuroyami.pingy.utils.MAX_INTERVAL_MS
import com.yuroyami.pingy.utils.loggye
import com.yuroyami.pingy.utils.pingyDataStoreDir
import com.yuroyami.pingy.utils.sanitizeIntervalMs
import com.yuroyami.pingy.utils.sanitizePayloadSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath

/** Hard cap on restored panels. Each one owns a socket, a thread and history. */
const val MAX_PANELS: Int = 24

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
) {
    /**
     * Force every field back into its supported range.
     *
     * Persisted values cross a trust boundary: the file is editable, can be
     * corrupted, and can outlive a schema change. An unchecked `Long.MAX_VALUE`
     * interval overflows the engine's deadline arithmetic into a negative
     * number, which spins the loop at full CPU and sends as fast as the socket
     * allows. Returns null when the record is not salvageable.
     */
    fun validated(): PanelSpec? {
        // One parser for typed and restored targets. A hand-edited or legacy
        // file could otherwise restore a value the UI refuses, such as an
        // address carrying credentials.
        val host = (parseTarget(ip) as? TargetParse.Valid)?.host ?: return null
        return copy(
            ip = host,
            intervalMs = sanitizeIntervalMs(intervalMs),
            packetSize = sanitizePayloadSize(packetSize),
            roof = roof.coerceIn(50, 10_000),
            angleOfAttack = if (angleOfAttack.isFinite()) angleOfAttack.coerceIn(0f, 20f)
                            else PingPanel.DEFAULT_ANGLE_OF_ATTACK,
            timeframeMs = timeframeMs.coerceIn(1_000L, 300_000L),
            canvasHeightFraction = if (canvasHeightFraction.isFinite())
                canvasHeightFraction.coerceIn(0.05f, 0.9f)
            else PingPanel.DEFAULT_CANVAS_HEIGHT_FRACTION,
        )
    }
}

/**
 * The result of reading the store. A failed read is deliberately *not* the same
 * value as an empty store: treating them alike meant a corrupt file silently
 * became "no panels", which then got overwritten with defaults on the next save,
 * destroying whatever the user actually had.
 */
sealed interface StoreLoad {
    /** No store has ever been written. First run. */
    data object NotInitialized : StoreLoad

    /** Read succeeded. [panels] may legitimately be empty. */
    data class Loaded(
        val panels: List<PanelSpec>,
        val graphStyle: String?,
        val panelLayout: String?,
        val droppedRecords: Int,
    ) : StoreLoad

    /** The store exists but could not be read or decoded. Never overwrite it. */
    data class Failed(val message: String) : StoreLoad
}

/** File name of the preferences store, shared by the platform directory actuals. */
const val STORE_FILE_NAME: String = "pingy.preferences_pb"

internal val json = Json { ignoreUnknownKeys = true }

internal val KEY_PANELS = stringPreferencesKey("panels")
internal val KEY_STYLE = stringPreferencesKey("graph_style")
internal val KEY_LAYOUT = stringPreferencesKey("panel_layout")
internal val KEY_INITIALIZED = booleanPreferencesKey("initialized")

/**
 * Turn a decoded preferences map into a [StoreLoad].
 *
 * Files written before the marker existed hold panels but no marker. They are
 * an initialized store, not a first run, so the upgrade must not report them
 * as empty and then overwrite them on the next save.
 */
internal fun decodeStore(prefs: Preferences): StoreLoad {
    val hasLegacyKeys =
        prefs[KEY_PANELS] != null || prefs[KEY_STYLE] != null || prefs[KEY_LAYOUT] != null
    if (prefs[KEY_INITIALIZED] != true && !hasLegacyKeys) return StoreLoad.NotInitialized

    val raw = prefs[KEY_PANELS]
        ?.let { json.decodeFromString<List<PanelSpec>>(it) }
        ?: emptyList()
    val valid = raw.mapNotNull { it.validated() }.take(MAX_PANELS)

    return StoreLoad.Loaded(
        panels = valid,
        graphStyle = prefs[KEY_STYLE],
        panelLayout = prefs[KEY_LAYOUT],
        droppedRecords = raw.size - valid.size,
    )
}

/** Preferences DataStore wrapper. One instance per process. */
object PingyStore {

    private val store: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.createWithPath {
            "${pingyDataStoreDir()}/$STORE_FILE_NAME".toPath()
        }
    }

    // Cancellation is rethrown rather than reported as a read or write fault.
    // Catching it turned "the caller went away" into "your file is corrupt".
    suspend fun load(): StoreLoad = try {
        decodeStore(store.data.first())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        loggye("PingyStore: load failed; keeping the existing file untouched", e)
        StoreLoad.Failed(e.message ?: e::class.simpleName ?: "unknown read failure")
    }

    /** Returns true when the write actually landed. */
    suspend fun save(panels: List<PanelSpec>, graphStyle: String, panelLayout: String): Boolean = try {
        store.edit { prefs ->
            prefs[KEY_INITIALIZED] = true
            prefs[KEY_PANELS] = json.encodeToString(panels.take(MAX_PANELS))
            prefs[KEY_STYLE] = graphStyle
            prefs[KEY_LAYOUT] = panelLayout
        }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        loggye("PingyStore: save failed", e)
        false
    }
}
