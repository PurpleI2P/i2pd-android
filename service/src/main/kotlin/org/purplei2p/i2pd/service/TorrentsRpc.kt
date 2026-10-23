package org.purplei2p.i2pd.service

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.purplei2p.i2pd.config.TunnelInfo
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Transmission status codes the torrents RPC of i2pd reports (TorrentStatus in Torrents.h). */
enum class TorrentStatus(val code: Int) {
    STOPPED(0), QUEUED_TO_VERIFY(1), VERIFYING(2), QUEUED_TO_DOWNLOAD(3), DOWNLOADING(4), QUEUED_TO_SEED(5), SEEDING(6);

    companion object {
        fun of(code: Int): TorrentStatus = entries.firstOrNull { it.code == code } ?: STOPPED
    }
}

/** One torrent of a torrents tunnel, as torrent-get gives it. Sizes in bytes, rates in bytes per second. */
data class TorrentItem(
    val id: Int,
    val name: String,
    val status: TorrentStatus,
    val percentDone: Double,
    /** 0 until the metadata of a magnet link is received. */
    val metadataPercent: Double,
    val sizeWhenDone: Long,
    val leftUntilDone: Long,
    val rateDownload: Long,
    val rateUpload: Long,
    val peersConnected: Int,
    val peersSendingToUs: Int,
    val peersGettingFromUs: Int,
    /** Seconds, negative when unknown. */
    val eta: Long,
    val uploadRatio: Double,
    val error: String?,
)

/** A file of a torrent: [name] relative to the download folder of the tunnel, [length] in bytes. */
data class TorrentFile(val name: String, val length: Long)

data class TorrentsStats(val torrentCount: Int, val activeCount: Int, val downloadSpeed: Long, val uploadSpeed: Long)

/** Result of torrent-add: the torrent was added, or the same one is already there. */
data class TorrentAdded(val id: Int, val name: String, val duplicate: Boolean)

class TorrentsRpcException(message: String) : IOException(message)

/**
 * Client of the Transmission-compatible RPC server of an i2pd torrents tunnel (TorrentsRPC.cpp):
 * JSON requests {"method", "arguments", "tag"} posted to <rpcpath>/rpc, Basic authorization when the
 * tunnel has rpcauth. Unlike Transmission there is no X-Transmission-Session-Id handshake.
 * [post] sends a request body and returns the response body.
 */
class TorrentsRpc(private val post: suspend (String) -> String) {

    private var tag = 0

    private suspend fun call(method: String, arguments: JSONObject = JSONObject()): JSONObject {
        val request = JSONObject().put("method", method).put("arguments", arguments).put("tag", ++tag)
        val response = JSONObject(post(request.toString()))
        val result = response.optString("result")
        if (result != "success") throw TorrentsRpcException(result.ifEmpty { "No result" })
        return response.optJSONObject("arguments") ?: JSONObject()
    }

    suspend fun torrents(): List<TorrentItem> {
        val arguments = call("torrent-get", JSONObject().put("fields", JSONArray(FIELDS)))
        val list = arguments.optJSONArray("torrents") ?: return emptyList()
        return (0 until list.length()).map { parseTorrent(list.getJSONObject(it)) }
    }

    /** Files of a torrent, empty until the metadata of a magnet link is received. */
    suspend fun files(id: Int): List<TorrentFile> {
        val arguments = call("torrent-get", ids(listOf(id)).put("fields", JSONArray(listOf("id", "files"))))
        val files = arguments.optJSONArray("torrents")?.optJSONObject(0)?.optJSONArray("files") ?: return emptyList()
        return (0 until files.length()).map { files.getJSONObject(it).let { f -> TorrentFile(f.optString("name"), f.optLong("length")) } }
    }

    /** Adds a magnet link. */
    suspend fun addMagnet(link: String): TorrentAdded = added(call("torrent-add", JSONObject().put("filename", link)))

    /** Adds the content of a .torrent file. */
    suspend fun addTorrentFile(content: ByteArray): TorrentAdded =
        added(call("torrent-add", JSONObject().put("metainfo", Base64.encodeToString(content, Base64.NO_WRAP))))

    suspend fun start(ids: List<Int>) = call("torrent-start", ids(ids)).let { }

    suspend fun stop(ids: List<Int>) = call("torrent-stop", ids(ids)).let { }

    /** Starts every torrent of the tunnel: i2pd takes a request without "ids" as all of them. */
    suspend fun startAll() = call("torrent-start").let { }

    /** Stops every torrent of the tunnel. */
    suspend fun stopAll() = call("torrent-stop").let { }

    suspend fun reannounce(ids: List<Int>) = call("torrent-reannounce", ids(ids)).let { }

    /** Removes torrents, with their downloaded files when [deleteData]. */
    suspend fun remove(ids: List<Int>, deleteData: Boolean) =
        call("torrent-remove", ids(ids).put("delete-local-data", deleteData)).let { }

    suspend fun stats(): TorrentsStats {
        val arguments = call("session-stats")
        // Transmission has the speeds at the top level, i2pd only in "current-stats"
        val current = arguments.optJSONObject("current-stats")
        fun speed(key: String) = if (arguments.has(key)) arguments.optLong(key) else current?.optLong(key) ?: 0
        return TorrentsStats(
            torrentCount = arguments.optInt("torrentCount"),
            activeCount = arguments.optInt("activeTorrentCount"),
            downloadSpeed = speed("downloadSpeed"),
            uploadSpeed = speed("uploadSpeed"),
        )
    }

    /** Number of known DHT nodes, null when the tunnel has no DHT. */
    suspend fun dhtNodes(): Int? = call("dht-stats").let { if (it.has("numNodes")) it.optInt("numNodes") else null }

    // An empty id list would mean "all torrents" to i2pd: never sent by mistake
    private fun ids(ids: List<Int>): JSONObject {
        require(ids.isNotEmpty())
        return JSONObject().put("ids", JSONArray(ids))
    }

    private fun added(arguments: JSONObject): TorrentAdded {
        arguments.optJSONObject("torrent-added")?.let { return TorrentAdded(it.optInt("id"), it.optString("name"), duplicate = false) }
        arguments.optJSONObject("torrent-duplicate")?.let { return TorrentAdded(it.optInt("id"), it.optString("name"), duplicate = true) }
        throw TorrentsRpcException("No torrent in the response")
    }

    private fun parseTorrent(t: JSONObject) = TorrentItem(
        id = t.optInt("id"),
        name = t.optString("name"),
        status = TorrentStatus.of(t.optInt("status")),
        percentDone = t.optDouble("percentDone", 0.0),
        metadataPercent = t.optDouble("metadataPercentComplete", 1.0),
        sizeWhenDone = t.optLong("sizeWhenDone"),
        leftUntilDone = t.optLong("leftUntilDone"),
        rateDownload = t.optLong("rateDownload"),
        rateUpload = t.optLong("rateUpload"),
        peersConnected = t.optInt("peersConnected"),
        peersSendingToUs = t.optInt("peersSendingToUs"),
        peersGettingFromUs = t.optInt("peersGettingFromUs"),
        eta = t.optLong("eta", -1),
        uploadRatio = t.optDouble("uploadRatio", 0.0),
        error = t.optString("errorString").takeIf { t.optInt("error") != 0 && it.isNotEmpty() },
    )

    companion object {
        /** Fields i2pd fills for real (others of Transmission are constants there). */
        private val FIELDS = listOf(
            "id", "name", "status", "percentDone", "metadataPercentComplete", "sizeWhenDone", "leftUntilDone",
            "rateDownload", "rateUpload", "peersConnected", "peersSendingToUs", "peersGettingFromUs",
            "eta", "uploadRatio", "error", "errorString",
        )

        /**
         * Client for torrents tunnel [tunnel], null without an RPC port. A wildcard RPC address is
         * reached through loopback.
         */
        fun forTunnel(tunnel: TunnelInfo): TorrentsRpc? {
            val values = tunnel.values
            val port = values["rpcport"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            val address = values["rpcaddress"].orEmpty().ifEmpty { "127.0.0.1" }
                .let { if (it == "0.0.0.0" || it == "::") "127.0.0.1" else it }
            val host = if (':' in address) "[$address]" else address
            val path = values["rpcpath"].orEmpty().trim('/').let { if (it.isEmpty()) "/rpc" else "/$it/rpc" }
            val auth = if (values["rpcauth"].let { it == "true" || it == "1" }) {
                "Basic " + Base64.encodeToString("${values["rpcuser"].orEmpty()}:${values["rpcpass"].orEmpty()}".toByteArray(), Base64.NO_WRAP)
            } else {
                null
            }
            return TorrentsRpc(httpPost(URL("http://$host:$port$path"), auth))
        }

        private fun httpPost(url: URL, auth: String?): suspend (String) -> String = { body ->
            withContext(Dispatchers.IO) {
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 15000
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    auth?.let { connection.setRequestProperty("Authorization", it) }
                    connection.outputStream.use { it.write(body.toByteArray()) }
                    when (val code = connection.responseCode) {
                        HttpURLConnection.HTTP_OK -> connection.inputStream.use { it.readBytes().decodeToString() }
                        HttpURLConnection.HTTP_UNAUTHORIZED -> throw TorrentsRpcException("Unauthorized")
                        else -> throw TorrentsRpcException("HTTP $code")
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }
    }
}
