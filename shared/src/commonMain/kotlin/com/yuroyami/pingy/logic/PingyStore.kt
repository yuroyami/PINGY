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
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath

/** Hard cap on restored panels. Each one owns a socket, a thread and history. */
const val MAX_PANELS: Int = 24

/**
 * Ceiling on the encoded panel list before it is parsed.
 *
 * Twenty-four full records run to a few thousand characters, so this leaves a
 * wide margin over anything the app itself writes.
 */
private const val MAX_PANELS_JSON_CHARS = 64_000

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
        val reduceMotion: Boolean = false,
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
internal val KEY_REDUCE_MOTION = booleanPreferencesKey("reduce_motion")

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

    val encoded = prefs[KEY_PANELS]
    // The 24 panel cap is applied after the whole list is parsed and mapped, so
    // an oversized file would be materialized in full before anything rejected
    // it. A normal save cannot come close to this bound.
    if (encoded != null && encoded.length > MAX_PANELS_JSON_CHARS) {
        return StoreLoad.Failed("saved panel list is too large to trust")
    }

    val raw = encoded
        ?.let { json.decodeFromString<List<PanelSpec>>(it) }
        ?: emptyList()
    val valid = raw.mapNotNull { it.validated() }.take(MAX_PANELS)

    return StoreLoad.Loaded(
        panels = valid,
        graphStyle = prefs[KEY_STYLE],
        panelLayout = prefs[KEY_LAYOUT],
        droppedRecords = raw.size - valid.size,
        reduceMotion = prefs[KEY_REDUCE_MOTION] == true,
    )
}

/**
 * Reading and writing the saved cockpit.
 *
 * An interface so the view model can be tested against a store that fails,
 * stalls or corrupts on demand, which is where the interesting bugs are.
 */
interface StoreApi {
    suspend fun load(): StoreLoad

    /** Returns true when the write actually landed. */
    suspend fun save(
        panels: List<PanelSpec>,
        graphStyle: String,
        panelLayout: String,
        reduceMotion: Boolean,
    ): Boolean

    /**
     * Move an unreadable store aside so a fresh one can be written, and return
     * the name it was kept under. Never deletes: the old bytes are the only
     * copy of whatever the user had.
     */
    suspend fun quarantine(): String?
}

/** Preferences DataStore wrapper. One instance per process. */
object PingyStore : StoreApi {

    private val storeDir: String by lazy { pingyDataStoreDir() }

    /** Where the preferences live. Exposed for tests that stage a broken file. */
    internal val storeFilePath: String get() = "$storeDir/$STORE_FILE_NAME"

    // Held rather than lazy, so quarantining a broken file can drop the cached
    // instance. DataStore caches its read, so a fresh one is the only way to
    // see that the file underneath is gone. Each instance owns a scope, because
    // DataStore refuses a second instance for a file until the first one's
    // scope has completed.
    @Volatile
    private var instance: DataStore<Preferences>? = null

    @Volatile
    private var instanceScope: CoroutineScope? = null

    private val store: DataStore<Preferences>
        get() = instance ?: run {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            instanceScope = scope
            PreferenceDataStoreFactory
                .createWithPath(scope = scope) { storeFilePath.toPath() }
                .also { instance = it }
        }

    // Cancellation is rethrown rather than reported as a read or write fault.
    // Catching it turned "the caller went away" into "your file is corrupt".
    override suspend fun load(): StoreLoad = try {
        decodeStore(store.data.first())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        loggye("PingyStore: load failed; keeping the existing file untouched", e)
        StoreLoad.Failed(e.message ?: e::class.simpleName ?: "unknown read failure")
    }

    override suspend fun save(
        panels: List<PanelSpec>,
        graphStyle: String,
        panelLayout: String,
        reduceMotion: Boolean,
    ): Boolean = try {
        store.edit { prefs ->
            prefs[KEY_INITIALIZED] = true
            prefs[KEY_PANELS] = json.encodeToString(panels.take(MAX_PANELS))
            prefs[KEY_STYLE] = graphStyle
            prefs[KEY_LAYOUT] = panelLayout
            prefs[KEY_REDUCE_MOTION] = reduceMotion
        }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        loggye("PingyStore: save failed", e)
        false
    }

    override suspend fun quarantine(): String? = try {
        val fs = FileSystem.SYSTEM
        val from = storeFilePath.toPath()
        val moved = if (fs.exists(from)) {
            var name = "$STORE_FILE_NAME.bak"
            var n = 1
            while (fs.exists("$storeDir/$name".toPath())) {
                name = "$STORE_FILE_NAME.bak$n"
                n++
            }
            fs.atomicMove(from, "$storeDir/$name".toPath())
            name
        } else {
            null
        }
        // Always start the reader over, whether or not there was a file: a
        // cached instance would go on serving the read that failed.
        releaseInstance()
        moved
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        loggye("PingyStore: could not move the unreadable store aside", e)
        null
    }

    /** Drop the cached instance so the next read opens the file again. */
    private fun releaseInstance() {
        instanceScope?.cancel()
        instanceScope = null
        instance = null
    }
}
