package org.purplei2p.i2pd

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.purplei2p.i2pd.service.TorrentsRpcException
import java.io.FileNotFoundException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * A failure as people read it: a translated sentence for the usual causes, with the system's own text
 * after it (useful in bug reports); failures of other kinds keep only the system's text, as before.
 */
@Composable
fun errorText(error: Throwable): String {
    val detail = error.message ?: error.javaClass.simpleName
    val reason = errorReason(error) ?: return detail
    return stringResource(R.string.error_with_detail, stringResource(reason), detail)
}

/**
 * The usual cause of [error] or of one of its causes, null when unknown. File errors of Android come as
 * IOException or ErrnoException whose message carries the errno name, such as
 * "open failed: ENOSPC (No space left on device)".
 */
fun errorReason(error: Throwable): Int? {
    for (e in generateSequence(error) { it.cause }.take(8)) {
        val message = e.message.orEmpty()
        when {
            e is TorrentsRpcException -> return R.string.error_refused
            e is SocketTimeoutException -> return R.string.error_timeout
            e is ConnectException || e is NoRouteToHostException || e is UnknownHostException -> return R.string.error_network
            "ENOSPC" in message || "EDQUOT" in message || "No space left" in message -> return R.string.error_no_space
            e is SecurityException || "EACCES" in message || "EPERM" in message || "EROFS" in message ||
                "Permission denied" in message || "Read-only file system" in message -> return R.string.error_no_access
            e is FileNotFoundException || "ENOENT" in message || "No such file" in message -> return R.string.error_not_found
            e is InterruptedIOException -> return R.string.error_timeout
        }
    }
    return null
}
