package org.miker.floatsauce

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import co.touchlab.kermit.Logger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL

@OptIn(UnstableApi::class)
class FloatsauceDataSource(
    private val upstream: DataSource,
    private val cookieName: String,
    private val cookieValue: String,
    private val origin: String,
    private val userAgent: String
) : DataSource {

    private var currentUri: Uri? = null
    private var isManifest: Boolean = false
    private var rewrittenInputStream: ByteArrayInputStream? = null

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        currentUri = uri

        if (uri.scheme == "floatsauce") {
            val httpsUri = uri.buildUpon().scheme("https").build()
            Logger.d { "FloatsauceDataSource intercepting: $httpsUri" }

            val newHeaders = dataSpec.httpRequestHeaders.toMutableMap()
            newHeaders["Cookie"] = "$cookieName=$cookieValue"
            newHeaders["User-Agent"] = userAgent
            newHeaders["Origin"] = origin

            val newSpec = dataSpec.buildUpon()
                .setUri(httpsUri)
                .setHttpRequestHeaders(newHeaders)
                .build()

            val responseLength = upstream.open(newSpec)

            // Check if it's a manifest
            val path = httpsUri.path ?: ""
            // Swift also checks mime type but we use path for simplicity here as it's common in HLS
            isManifest = path.endsWith(".m3u8")

            if (isManifest) {
                val data = readUpstreamFully()
                val manifestStr = data.toString(Charsets.UTF_8)
                val rewritten = rewriteManifest(manifestStr, httpsUri.toString())
                val rewrittenData = rewritten.toByteArray(Charsets.UTF_8)
                Logger.d { "FloatsauceDataSource modified manifest [${rewrittenData.size} bytes] for $httpsUri" }
                rewrittenInputStream = ByteArrayInputStream(rewrittenData)
                return rewrittenData.size.toLong()
            }

            return responseLength
        } else {
            return upstream.open(dataSpec)
        }
    }

    private fun readUpstreamFully(): ByteArray {
        val buffer = ByteArrayOutputStream()
        val data = ByteArray(8192)
        var bytesRead: Int
        while (upstream.read(data, 0, data.size).also { bytesRead = it } != -1) {
            buffer.write(data, 0, bytesRead)
        }
        return buffer.toByteArray()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (isManifest && rewrittenInputStream != null) {
            return rewrittenInputStream!!.read(buffer, offset, length)
        }
        return upstream.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        upstream.close()
        rewrittenInputStream = null
        isManifest = false
    }

    private fun rewriteManifest(manifest: String, baseUrl: String): String {
        val lines = manifest.lines()
        val processedLines = mutableListOf<String>()
        var lastLineWasStreamInf = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                processedLines.add(line)
                continue
            }

            if (trimmed.startsWith("#EXT-X-KEY") || trimmed.startsWith("#EXT-X-SESSION-KEY") ||
                trimmed.startsWith("#EXT-X-I-FRAME-STREAM-INF") || trimmed.startsWith("#EXT-X-MEDIA")
            ) {
                processedLines.add(rewriteAttributeUri(line, "floatsauce", baseUrl))
                lastLineWasStreamInf = false
            } else if (trimmed.startsWith("#EXT-X-MAP")) {
                processedLines.add(rewriteAttributeUri(line, "https", baseUrl))
                lastLineWasStreamInf = false
            } else if (trimmed.startsWith("#EXT-X-STREAM-INF")) {
                processedLines.add(line)
                lastLineWasStreamInf = true
            } else if (trimmed.startsWith("#")) {
                processedLines.add(line)
                lastLineWasStreamInf = false
            } else {
                // URI line
                val scheme = if (lastLineWasStreamInf) "floatsauce" else "https"
                processedLines.add(absoluteUri(trimmed, scheme, baseUrl))
                lastLineWasStreamInf = false
            }
        }
        return processedLines.joinToString("\n")
    }

    private fun rewriteAttributeUri(line: String, scheme: String, baseUrl: String): String {
        val uriRegex = Regex("""URI="([^"]+)"""")
        return uriRegex.replace(line) { matchResult ->
            val uri = matchResult.groupValues[1]
            val absolute = absoluteUri(uri, scheme, baseUrl)
            """URI="$absolute""""
        }
    }

    private fun absoluteUri(uri: String, scheme: String, baseUrl: String): String {
        return try {
            val absoluteUrl = if (uri.contains("://")) {
                URL(uri)
            } else {
                URL(URL(baseUrl), uri)
            }
            Uri.parse(absoluteUrl.toString()).buildUpon().scheme(scheme).build().toString()
        } catch (e: Exception) {
            uri
        }
    }
}
