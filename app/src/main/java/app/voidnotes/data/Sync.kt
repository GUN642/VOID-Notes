package app.voidnotes.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

enum class SyncState(val label: String) {
    CONNECTING("Verbinde …"),
    ONLINE("Live"),
    OFFLINE("Offline"),
    ERROR("Fehler"),
}

class SyncException(message: String) : IOException(message)

/** What this device last saw (or wrote) for a block: content hash and server time. */
@Serializable
data class Known(val h: String, val t: Long)

@Serializable
data class SyncMemory(
    val known: Map<String, Known> = emptyMap(),
    val meta: Known? = null,
    /** Attachments already uploaded. */
    val uploaded: Set<String> = emptySet(),
)

/** Minimal REST client for a Firebase Realtime Database. */
class FirebaseRest(private val host: String) {
    private fun url(path: String) = URL("https://$host/$path.json")

    private fun open(path: String, method: String): HttpURLConnection {
        val c = url(path).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = true
        if (method == "PATCH") {
            c.requestMethod = "POST"
            c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        } else {
            c.requestMethod = method
        }
        return c
    }

    private fun fail(c: HttpURLConnection): Nothing {
        val code = c.responseCode
        val body = runCatching { c.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
        throw SyncException(
            when (code) {
                401, 403 -> "Zugriff verweigert – die Regeln der Firebase-Datenbank erlauben das nicht"
                404 -> "Datenbank nicht gefunden – Adresse prüfen"
                423 -> "Datenbank ist gesperrt (Firebase-Konsole prüfen)"
                else -> "Server antwortet mit $code ${body.take(120)}"
            },
        )
    }

    fun get(path: String): String? {
        val c = open(path, "GET")
        try {
            if (c.responseCode !in 200..299) fail(c)
            val text = c.inputStream.bufferedReader().use { it.readText() }
            return if (text == "null") null else text
        } finally {
            c.disconnect()
        }
    }

    fun send(path: String, method: String, body: String?) {
        val c = open(path, method)
        try {
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            if (c.responseCode !in 200..299) fail(c)
            c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    /** Server-sent events: blocks until the connection ends. */
    fun stream(path: String, onConnection: (HttpURLConnection) -> Unit, onEvent: (String, String) -> Unit) {
        val c = open(path, "GET")
        c.setRequestProperty("Accept", "text/event-stream")
        c.readTimeout = 90_000
        onConnection(c)
        try {
            if (c.responseCode !in 200..299) fail(c)
            c.inputStream.bufferedReader().use { r ->
                var event = ""
                while (true) {
                    val line = r.readLine() ?: break
                    when {
                        line.startsWith("event:") -> event = line.substring(6).trim()
                        line.startsWith("data:") -> onEvent(event, line.substring(5).trim())
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }
}

/**
 * Live synchronisation of one shared page. Every block is stored on its own (encrypted) so that
 * several people can edit different blocks at the same time; the newest version of a block wins.
 * All merging happens on the main thread, network on IO.
 */
class SyncSession(
    val pageId: String,
    val info: ShareInfo,
    private val store: Store,
    private val scope: CoroutineScope,
    /** Writes pending editor changes and returns the current page. */
    private val local: () -> Page?,
    /** Shows a merged page (store and open editor). */
    private val apply: (Page) -> Unit,
    /** Block that is being typed in right now ("#title" for the title). */
    private val focused: () -> String?,
) {
    var state by mutableStateOf(SyncState.CONNECTING)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val fb = FirebaseRest(info.host)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val path = "notes/${info.id}"
    private var mirror = JSONObject()
    private var loaded = false
    private var memory: SyncMemory = store.loadSync(pageId)
    private var job: Job? = null
    private var pushJob: Job? = null
    private var connection: HttpURLConnection? = null
    private val fetching = HashSet<String>()
    @Volatile private var stopped = false

    fun start() {
        if (job?.isActive == true) return
        stopped = false
        job = scope.launch {
            var backoff = 1_000L
            while (isActive && !stopped) {
                state = if (loaded) SyncState.OFFLINE else SyncState.CONNECTING
                try {
                    withContext(Dispatchers.IO) {
                        fb.stream(path, { connection = it }) { event, data ->
                            scope.launch { onEvent(event, data) }
                        }
                    }
                    backoff = 1_000L
                } catch (e: SyncException) {
                    error = e.message
                    state = SyncState.ERROR
                    backoff = 30_000L
                } catch (e: Exception) {
                    if (stopped) break
                    state = SyncState.OFFLINE
                }
                if (stopped) break
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }

    fun stop() {
        stopped = true
        job?.cancel()
        runCatching { connection?.disconnect() }
        connection = null
    }

    /** Local edit: push it shortly after. */
    fun localChanged() {
        if (!loaded) return
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(500)
            reconcile()
        }
    }

    // ------------------------------------------------------------------ events

    private fun onEvent(event: String, data: String) {
        when (event) {
            "put", "patch" -> {
                val obj = runCatching { JSONObject(data) }.getOrNull() ?: return
                val p = obj.optString("path", "/")
                val value = obj.opt("data")
                if (event == "put") setAt(p, value) else {
                    val patch = value as? JSONObject ?: return
                    patch.keys().forEach { k -> setAt(p.trimEnd('/') + "/" + k, patch.opt(k)) }
                }
                if (!loaded) loaded = true
                state = SyncState.ONLINE
                error = null
                reconcile()
            }
            "cancel", "auth_revoked" -> {
                error = "Zugriff verweigert – die Regeln der Firebase-Datenbank erlauben das nicht"
                state = SyncState.ERROR
            }
        }
    }

    private fun setAt(path: String, value: Any?) {
        val parts = path.split('/').filter { it.isNotEmpty() }
        val v = if (value == JSONObject.NULL) null else value
        if (parts.isEmpty()) {
            mirror = v as? JSONObject ?: JSONObject()
            return
        }
        var node = mirror
        for (part in parts.dropLast(1)) {
            val next = node.optJSONObject(part) ?: JSONObject().also { node.put(part, it) }
            node = next
        }
        if (v == null) node.remove(parts.last()) else node.put(parts.last(), v)
    }

    // ------------------------------------------------------------------ merging

    private data class Remote(val block: Block?, val order: Double, val time: Long, val deleted: Boolean)
    private data class Item(val block: Block, val order: Double, val tie: Int)

    private val tomb = "-"

    private fun payload(b: Block): String = json.encodeToString(Block.serializer(), b.copy(id = ""))
    private fun hashOf(b: Block, order: Double) = Crypto.hash(payload(b) + "|" + order)

    private fun remoteBlocks(): Map<String, Remote> {
        val obj = mirror.optJSONObject("b") ?: return emptyMap()
        val out = HashMap<String, Remote>()
        obj.keys().forEach { id ->
            val e = obj.optJSONObject(id) ?: return@forEach
            val time = e.optLong("t", 0)
            val deleted = e.optBoolean("d", false)
            val block = if (deleted) null else runCatching {
                json.decodeFromString(Block.serializer(), Crypto.decryptText(info.key, e.getString("c"))).copy(id = id)
            }.getOrNull()
            if (deleted || block != null) out[id] = Remote(block, e.optDouble("o", 0.0), time, deleted)
        }
        return out
    }

    private fun entry(b: Block, order: Double) = JSONObject()
        .put("c", Crypto.encryptText(info.key, payload(b)))
        .put("o", order)
        .put("t", JSONObject().put(".sv", "timestamp"))

    private fun tombEntry() = JSONObject().put("d", true).put("o", 0).put("t", JSONObject().put(".sv", "timestamp"))

    fun reconcile() {
        if (!loaded || stopped) return
        val page = local() ?: return
        val focus = focused()
        mirror.optJSONObject("m")?.let { m ->
            if (runCatching { Crypto.decryptText(info.key, m.getString("c")) }.isFailure) {
                error = "Der Schlüssel im Link passt nicht zu dieser Notiz"
                state = SyncState.ERROR
                return
            }
        }
        val remote = remoteBlocks()
        val known = memory.known.toMutableMap()
        val pushes = JSONObject()

        var localBlocks = page.blocks
        // A page that was just joined only holds an empty line – the shared content replaces it.
        if (memory.known.isEmpty() && remote.isNotEmpty() && localBlocks.size == 1 &&
            localBlocks[0].type == BlockType.TEXT && localBlocks[0].text.isEmpty()
        ) localBlocks = emptyList()

        // Positions count only synced blocks, so devices with different sub page links agree.
        val localOrder = HashMap<String, Double>()
        var n = 0
        localBlocks.forEach { b ->
            if (b.type == BlockType.PAGE) localOrder[b.id] = n - 0.5 else localOrder[b.id] = (n++).toDouble()
        }
        val localById = localBlocks.associateBy { it.id }
        val localIndex = localBlocks.withIndex().associate { it.value.id to it.index }

        val result = ArrayList<Item>()
        val ids = LinkedHashSet<String>().apply { localBlocks.forEach { add(it.id) }; addAll(remote.keys) }
        for (id in ids) {
            val l = localById[id]
            val r = remote[id]
            val k = known[id]
            val tie = localIndex[id] ?: Int.MAX_VALUE
            if (l?.type == BlockType.PAGE) {
                result += Item(l, localOrder[id]!!, tie); continue
            }
            val lOrder = localOrder[id] ?: 0.0
            val lh = l?.let { hashOf(it, lOrder) }
            val rh = r?.let { if (it.deleted) tomb else hashOf(it.block!!, it.order) }
            val localChanged = l != null && (k == null || lh != k.h)
            // Our own write coming back from the server is not a remote change.
            val echo = r != null && k != null && rh == k.h
            if (echo && r!!.time > k!!.t) known[id] = k.copy(t = r.time)
            val remoteNewer = r != null && !echo && (k == null || r.time > k.t)

            if (remoteNewer && !(localChanged && id == focus)) {
                if (r!!.deleted) {
                    known[id] = Known(tomb, r.time)
                } else {
                    result += Item(r.block!!, r.order, tie)
                    known[id] = Known(rh!!, r.time)
                }
            } else if (l != null) {
                result += Item(l, lOrder, tie)
                if (localChanged) {
                    pushes.put("b/$id", entry(l, lOrder))
                    known[id] = Known(lh!!, r?.time ?: 0)
                    l.file?.let { upload(it) }
                }
            } else if (k != null && k.h != tomb && r?.deleted != true) {
                // Deleted on this device.
                pushes.put("b/$id", tombEntry())
                known[id] = Known(tomb, r?.time ?: 0)
            }
        }
        val merged = result.sortedWith(compareBy<Item>({ it.order }, { it.tie }, { it.block.id })).map { it.block }

        // Title and icon
        var title = page.title
        var icon = page.icon
        val metaPayload = JSONObject().put("title", page.title).put("icon", page.icon).toString()
        val mh = Crypto.hash(metaPayload)
        val mk = memory.meta
        val m = mirror.optJSONObject("m")
        val rMeta = m?.let { runCatching { JSONObject(Crypto.decryptText(info.key, it.getString("c"))) }.getOrNull() }
        val rmh = rMeta?.let { Crypto.hash(JSONObject().put("title", it.optString("title")).put("icon", it.optString("icon")).toString()) }
        val rt = m?.optLong("t", 0) ?: 0
        var newMeta = mk
        val metaLocalChanged = mk == null || mh != mk.h
        val metaEcho = rmh != null && mk != null && rmh == mk.h
        if (metaEcho && rt > mk!!.t) newMeta = mk.copy(t = rt)
        val metaRemoteNewer = rMeta != null && !metaEcho && (mk == null || rt > mk.t)
        if (metaRemoteNewer && !(metaLocalChanged && focus == "#title" && mk != null)) {
            title = rMeta!!.optString("title")
            icon = rMeta.optString("icon")
            newMeta = Known(rmh!!, rt)
        } else if (metaLocalChanged) {
            pushes.put("m", JSONObject().put("c", Crypto.encryptText(info.key, metaPayload)).put("t", JSONObject().put(".sv", "timestamp")))
            newMeta = Known(mh, rt)
        }

        if (merged != page.blocks || title != page.title || icon != page.icon) {
            apply(page.copy(blocks = merged, title = title, icon = icon))
        }
        memory = memory.copy(known = known, meta = newMeta)
        store.saveSync(pageId, memory)
        if (pushes.length() > 0) push(pushes)
        merged.forEach { b -> b.file?.let { f -> if (!store.attachment(f).exists()) fetchFile(f) } }
    }

    private fun push(body: JSONObject) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { fb.send(path, "PATCH", body.toString()) }
            } catch (e: SyncException) {
                error = e.message; state = SyncState.ERROR
            } catch (e: Exception) {
                // Offline: forget what we thought was written so it is sent again.
                val failed = body.keys().asSequence().filter { it.startsWith("b/") }.map { it.removePrefix("b/") }.toSet()
                memory = memory.copy(known = memory.known - failed, meta = if (body.has("m")) null else memory.meta)
                store.saveSync(pageId, memory)
                state = SyncState.OFFLINE
            }
        }
    }

    // ------------------------------------------------------------------ attachments (up to 3 MB)

    private fun fileKey(name: String) = name.replace(Regex("[.#$\\[\\]/]"), "_")

    private fun upload(file: String) {
        if (file in memory.uploaded) return
        val f = store.attachment(file)
        if (!f.exists() || f.length() > 3L * 1024 * 1024) return
        memory = memory.copy(uploaded = memory.uploaded + file)
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val data = Crypto.encrypt(info.key, f.readBytes())
                    fb.send("$path/f/${fileKey(file)}", "PUT", JSONObject.quote(data))
                }
            } catch (e: Exception) {
                memory = memory.copy(uploaded = memory.uploaded - file)
            }
            store.saveSync(pageId, memory)
        }
    }

    private fun fetchFile(file: String) {
        if (!fetching.add(file)) return
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val raw = fb.get("$path/f/${fileKey(file)}") ?: return@withContext false
                    val bytes = Crypto.decrypt(info.key, JSONObject("{\"v\":$raw}").getString("v"))
                    store.attachment(file).writeBytes(bytes)
                    true
                }
            }.getOrDefault(false)
            if (ok) Store.fileVersion++ else fetching.remove(file)
        }
    }

    companion object {
        fun deleteRemote(info: ShareInfo) = FirebaseRest(info.host).send("notes/${info.id}", "DELETE", null)
    }
}

/** Database rules to paste into the Firebase console. */
const val FIREBASE_RULES = """{
  "rules": {
    ".read": false,
    ".write": false,
    "notes": {
      "${'$'}id": {
        ".read": "${'$'}id.length >= 20",
        ".write": "${'$'}id.length >= 20"
      }
    }
  }
}"""

fun normalizeHost(input: String): String = input.trim()
    .removePrefix("https://").removePrefix("http://")
    .substringBefore('/').lowercase()
