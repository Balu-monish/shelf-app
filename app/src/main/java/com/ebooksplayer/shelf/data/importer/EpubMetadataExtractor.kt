package com.ebooksplayer.shelf.data.importer

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Reads just enough of an EPUB (a zip of XHTML/XML files) to get its title,
 * author, and cover image, without pulling in a full EPUB rendering
 * library. The heavier Readium toolkit is added in Phase 3 for actual
 * on-screen rendering.
 */
@Singleton
class EpubMetadataExtractor @Inject constructor(
    private val contentResolver: ContentResolver,
) {
    suspend fun extract(uri: Uri, fallbackTitle: String): ExtractedMetadata {
        val opfPath = findOpfPath(uri) ?: return ExtractedMetadata(fallbackTitle, null, null)
        val opfDoc = readZipEntryAsXml(uri, opfPath) ?: return ExtractedMetadata(fallbackTitle, null, null)

        val title = firstElementText(opfDoc, "title")?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackTitle
        val author = firstElementText(opfDoc, "creator")?.trim()?.takeIf { it.isNotEmpty() }
        val coverHref = findCoverHref(opfDoc)
        val coverBytes = coverHref?.let { href ->
            val basePath = opfPath.substringBeforeLast('/', "")
            val coverPath = resolveRelativePath(basePath, href)
            readZipEntryBytes(uri, coverPath)
        }
        // Calibre-only convention (no EPUB standard covers series membership).
        val series = findMetaContent(opfDoc, "calibre:series")
        val seriesIndex = findMetaContent(opfDoc, "calibre:series_index")?.toFloatOrNull()

        return ExtractedMetadata(
            title = title,
            author = author,
            coverBytes = coverBytes,
            series = series,
            seriesIndex = seriesIndex,
        )
    }

    private fun findOpfPath(uri: Uri): String? {
        val doc = readZipEntryAsXml(uri, "META-INF/container.xml") ?: return null
        val rootFiles = doc.getElementsByTagName("rootfile")
        for (i in 0 until rootFiles.length) {
            val element = rootFiles.item(i) as? Element ?: continue
            val fullPath = element.getAttribute("full-path")
            if (fullPath.isNotBlank()) return fullPath
        }
        return null
    }

    private fun findCoverHref(opfDoc: Document): String? {
        val items = opfDoc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val element = items.item(i) as? Element ?: continue
            val properties = element.getAttribute("properties")
            if (properties.contains("cover-image")) {
                return element.getAttribute("href").takeIf { it.isNotBlank() }
            }
        }

        var coverId: String? = null
        val metas = opfDoc.getElementsByTagName("meta")
        for (i in 0 until metas.length) {
            val element = metas.item(i) as? Element ?: continue
            if (element.getAttribute("name") == "cover") {
                coverId = element.getAttribute("content").takeIf { it.isNotBlank() }
                break
            }
        }
        if (coverId == null) return null

        for (i in 0 until items.length) {
            val element = items.item(i) as? Element ?: continue
            if (element.getAttribute("id") == coverId) {
                return element.getAttribute("href").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun findMetaContent(opfDoc: Document, metaName: String): String? {
        val metas = opfDoc.getElementsByTagName("meta")
        for (i in 0 until metas.length) {
            val element = metas.item(i) as? Element ?: continue
            if (element.getAttribute("name") == metaName) {
                return element.getAttribute("content").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun firstElementText(doc: Document, localName: String): String? {
        val all = doc.getElementsByTagName("*")
        for (i in 0 until all.length) {
            val element = all.item(i) as? Element ?: continue
            val tag = element.tagName.substringAfter(':')
            if (tag.equals(localName, ignoreCase = true)) {
                return element.textContent
            }
        }
        return null
    }

    private fun resolveRelativePath(basePath: String, href: String): String =
        if (basePath.isEmpty()) href else "$basePath/$href"

    private fun readZipEntryAsXml(uri: Uri, entryPath: String): Document? {
        val bytes = readZipEntryBytes(uri, entryPath) ?: return null
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
            factory.newDocumentBuilder().parse(bytes.inputStream())
        }.onFailure { Log.w(TAG, "Failed to parse $entryPath", it) }.getOrNull()
    }

    private fun readZipEntryBytes(uri: Uri, entryPath: String): ByteArray? {
        return openZip(uri) { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == entryPath) {
                    return@openZip zip.readBytes()
                }
                entry = zip.nextEntry
            }
            null
        }
    }

    private fun <T> openZip(uri: Uri, block: (ZipInputStream) -> T?): T? {
        return try {
            val stream = contentResolver.openInputStream(uri) ?: return null
            ZipInputStream(stream).use(block)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read EPUB zip", e)
            null
        }
    }

    companion object {
        private const val TAG = "EpubMetadataExtractor"
    }
}
