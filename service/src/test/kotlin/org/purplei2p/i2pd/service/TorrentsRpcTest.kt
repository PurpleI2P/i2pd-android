package org.purplei2p.i2pd.service

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.purplei2p.i2pd.config.TunnelInfo

class TorrentsRpcTest {

    /** Records requests and answers with [responses] in turn, as JSON texts of TorrentsRPC.cpp. */
    private class FakeServer(vararg responses: String) {
        val requests = mutableListOf<JSONObject>()
        private val queue = ArrayDeque(responses.toList())
        val post: suspend (String) -> String = { body ->
            requests += JSONObject(body)
            queue.removeFirst()
        }
    }

    @Test
    fun `torrent list is read from torrent-get`() = runTest {
        val server = FakeServer(
            """{"result":"success","arguments":{"torrents":[{"id":1,"name":"ubuntu.iso","status":4,"percentDone":0.25,
               "metadataPercentComplete":1.0,"sizeWhenDone":1000,"leftUntilDone":750,"rateDownload":2048,"rateUpload":10,
               "peersConnected":3,"peersSendingToUs":2,"peersGettingFromUs":1,"eta":120,"uploadRatio":0.5,
               "error":0,"errorString":"none"}]},"tag":1}""",
        )
        val torrents = TorrentsRpc(server.post).torrents()
        val request = server.requests.single()
        assertEquals("torrent-get", request.getString("method"))
        assertTrue(request.getJSONObject("arguments").getJSONArray("fields").toString().contains("\"percentDone\""))
        val t = torrents.single()
        assertEquals(1, t.id)
        assertEquals(TorrentStatus.DOWNLOADING, t.status)
        assertEquals(0.25, t.percentDone, 0.0)
        assertEquals(2048L, t.rateDownload)
        assertEquals(3, t.peersConnected)
        // "none" with error 0 is not an error
        assertNull(t.error)
    }

    @Test
    fun `magnet links are added through filename and duplicates are told apart`() = runTest {
        val server = FakeServer(
            """{"result":"success","arguments":{"torrent-added":{"id":2,"hashString":"AB","name":"a"}},"tag":1}""",
            """{"result":"success","arguments":{"torrent-duplicate":{"id":2,"hashString":"AB","name":"a"}},"tag":2}""",
        )
        val rpc = TorrentsRpc(server.post)
        assertEquals(TorrentAdded(2, "a", duplicate = false), rpc.addMagnet("magnet:?xt=urn:btih:ab"))
        assertEquals(TorrentAdded(2, "a", duplicate = true), rpc.addMagnet("magnet:?xt=urn:btih:ab"))
        assertEquals("magnet:?xt=urn:btih:ab", server.requests[0].getJSONObject("arguments").getString("filename"))
    }

    @Test
    fun `errors and removal with data are passed through`() = runTest {
        val server = FakeServer(
            """{"result":"success","arguments":{},"tag":1}""",
            """{"result":"invalid or corrupt torrent file","arguments":{},"tag":2}""",
        )
        val rpc = TorrentsRpc(server.post)
        rpc.remove(listOf(3), deleteData = true)
        val arguments = server.requests[0].getJSONObject("arguments")
        assertEquals("[3]", arguments.getJSONArray("ids").toString())
        assertTrue(arguments.getBoolean("delete-local-data"))
        val error = runCatching { rpc.addMagnet("bad") }.exceptionOrNull()
        assertEquals("invalid or corrupt torrent file", error?.message)
    }

    @Test
    fun `no client without an RPC port`() {
        fun tunnel(vararg values: Pair<String, String>) = TunnelInfo("t", "torrents", mapOf(*values), null, emptyList())
        assertNull(TorrentsRpc.forTunnel(tunnel("torrentsdir" to "/d")))
        assertNull(TorrentsRpc.forTunnel(tunnel("rpcport" to "0")))
        assertTrue(TorrentsRpc.forTunnel(tunnel("rpcport" to "9091")) != null)
    }
}
