package org.purplei2p.i2pd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.purplei2p.i2pd.service.TorrentsRpcException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

class ErrorTextTest {

    @Test
    fun `errno names in messages of file errors`() {
        assertEquals(R.string.error_no_space, errorReason(IOException("write failed: ENOSPC (No space left on device)")))
        assertEquals(R.string.error_no_access, errorReason(FileNotFoundException("/storage/X/a: open failed: EACCES (Permission denied)")))
        assertEquals(R.string.error_no_access, errorReason(IOException("open failed: EROFS (Read-only file system)")))
        assertEquals(R.string.error_not_found, errorReason(FileNotFoundException("/data/a: open failed: ENOENT (No such file or directory)")))
    }

    @Test
    fun `network and router failures`() {
        assertEquals(R.string.error_network, errorReason(ConnectException("failed to connect to /127.0.0.1 (port 9191)")))
        assertEquals(R.string.error_timeout, errorReason(SocketTimeoutException("timeout")))
        assertEquals(R.string.error_refused, errorReason(TorrentsRpcException("Method not found")))
    }

    @Test
    fun `the cause counts when the wrapper says nothing`() {
        assertEquals(R.string.error_no_space, errorReason(IOException("Copy failed", IOException("ENOSPC"))))
    }

    @Test
    fun `unknown failures keep only their own text`() {
        assertNull(errorReason(IOException("Cannot create /a/b")))
        assertNull(errorReason(IllegalStateException()))
    }
}
