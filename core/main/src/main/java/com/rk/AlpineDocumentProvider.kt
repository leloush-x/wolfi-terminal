package com.rk

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.util.Log
import android.webkit.MimeTypeMap
import com.rk.libcommons.alpineHomeDir
import com.rk.libcommons.debianHomeDir
import com.rk.libcommons.voidHomeDir
import com.rk.libcommons.wolfiHomeDir
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Collections
import java.util.LinkedList
import java.util.Locale
import com.rk.terminal.R

class AlpineDocumentProvider : DocumentsProvider() {

    // Multi-root SAF: one root per distro home. DocIds stay absolute paths
    // on purpose (flexible, writable, backward compatible with the old
    // single Alpine root which also used absolute paths).

    private fun allRoots(): List<Triple<String, String, File>> {
        val ctx = context!!
        // Ensure dirs exist so Files app never sees a dead root.
        // Flexible: every distro always visible, always writable.
        return listOf(
            Triple("alpine", "Alpine", ctx.alpineHomeDir()),
            Triple("wolfi", "Wolfi", ctx.wolfiHomeDir()),
            Triple("debian", "Debian", ctx.debianHomeDir()),
            Triple("void", "Void", ctx.voidHomeDir()),
        )
    }

    private fun dirForRootId(rootId: String): File {
        allRoots().firstOrNull { it.first == rootId }?.let { return it.third }
        // Legacy fallback: old installs used the absolute path as rootId.
        val f = File(rootId)
        if (f.exists()) return f
        return context!!.alpineHomeDir()
    }

    override fun queryRoots(projection: Array<String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        for ((rootId, name, dir) in allRoots()) {
            val row = result.newRow()
            row.add(DocumentsContract.Root.COLUMN_ROOT_ID, rootId)
            row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, getDocIdForFile(dir))
            row.add(DocumentsContract.Root.COLUMN_SUMMARY, dir.absolutePath)
            row.add(
                DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.FLAG_SUPPORTS_CREATE or DocumentsContract.Root.FLAG_SUPPORTS_SEARCH or DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
            )
            row.add(DocumentsContract.Root.COLUMN_TITLE, "Wolfi Terminal — $name")
            row.add(DocumentsContract.Root.COLUMN_MIME_TYPES, ALL_MIME_TYPES)
            row.add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, dir.freeSpace)
            row.add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher)
        }
        return result
    }

    @Throws(FileNotFoundException::class)
    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        includeFile(result, documentId, null)
        return result
    }

    @Throws(FileNotFoundException::class)
    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val parent = getFileForDocId(parentDocumentId)
        val files = parent.listFiles()
        if (files != null) {
            for (file in files) {
                includeFile(result, null, file)
            }
        } else {
            Log.e("DocumentsProvider", "Unable to list files in $parentDocumentId")
        }
        // Live refresh: Files app re-queries when we notifyChange this URI.
        runCatching {
            val ctx = context
            if (ctx != null) {
                result.setNotificationUri(
                    ctx.contentResolver,
                    DocumentsContract.buildChildDocumentsUri(authority(), parentDocumentId)
                )
            }
        }
        return result
    }

    @Throws(FileNotFoundException::class)
    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = getFileForDocId(documentId)
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        return ParcelFileDescriptor.open(file, accessMode)
    }

    @Throws(FileNotFoundException::class)
    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point,
        signal: CancellationSignal
    ): AssetFileDescriptor {
        val file = getFileForDocId(documentId)
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, file.length())
    }

    override fun onCreate(): Boolean = true

    @Throws(FileNotFoundException::class)
    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String {
        val parent = getFileForDocId(parentDocumentId)
        var newFile = File(parent, displayName)
        var noConflictId = 2
        while (newFile.exists()) {
            newFile = File(parent, "$displayName ($noConflictId)")
            noConflictId++
        }
        try {
            val succeeded = if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType) {
                newFile.mkdir()
            } else {
                newFile.createNewFile()
            }
            if (!succeeded) {
                throw FileNotFoundException("Failed to create document with id " + newFile.absolutePath)
            }
        } catch (e: IOException) {
            throw FileNotFoundException("Failed to create document with id " + newFile.absolutePath)
        }
        notifyChanged(parentDocumentId, newFile)
        return getDocIdForFile(newFile)
    }

    @Throws(FileNotFoundException::class)
    override fun deleteDocument(documentId: String) {
        val file = getFileForDocId(documentId)
        val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
        if (!ok && file.exists()) {
            throw FileNotFoundException("Failed to delete document with id $documentId")
        }
        notifyChanged(documentId)
    }

    @Throws(FileNotFoundException::class)
    override fun getDocumentType(documentId: String): String {
        val file = getFileForDocId(documentId)
        return getMimeType(file)
    }

    @Throws(FileNotFoundException::class)
    override fun querySearchDocuments(
        rootId: String,
        query: String,
        projection: Array<String>?
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        // rootId is now "alpine"/"wolfi"/"debian"/"void" — resolve to dir.
        val parent = try {
            dirForRootId(rootId)
        } catch (e: FileNotFoundException) {
            try { getFileForDocId(rootId) } catch (e2: FileNotFoundException) { return result }
        }
        val pending = LinkedList<File>()
        pending.add(parent)

        val maxResults = 50
        val q = query.lowercase(Locale.getDefault())
        while (!pending.isEmpty() && result.count < maxResults) {
            val file = pending.removeFirst()
            // Flexible: search everything under this root, no containment gate.
            // Directories that match are listed too (browsable), plus recursion.
            if (file.name.lowercase(Locale.getDefault()).contains(q)) {
                includeFile(result, null, file)
                if (result.count >= maxResults) break
            }
            if (file.isDirectory) {
                file.listFiles()?.let { Collections.addAll(pending, *it) }
            }
        }
        return result
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        // Flexible: pure prefix with separator ("/a/b" is parent of "/a/b/c",
        // but NOT of "/a/bc"). No canonical containment gate per request.
        if (documentId == parentDocumentId) return true
        val prefix = parentDocumentId.trimEnd('/') + "/"
        return documentId.startsWith(prefix)
    }

    @Throws(FileNotFoundException::class)
    override fun renameDocument(documentId: String, displayName: String): String {
        val file = getFileForDocId(documentId)
        val parent = file.parentFile ?: throw FileNotFoundException("No parent for $documentId")
        val target = File(parent, displayName)
        if (target.exists()) throw FileNotFoundException("Target exists: ${target.absolutePath}")
        if (!file.renameTo(target)) {
            // Flexible fallback: copy + delete (covers cross-mount renames).
            if (file.isDirectory) {
                file.copyRecursively(target, overwrite = false)
                file.deleteRecursively()
            } else {
                file.copyTo(target, overwrite = false)
                file.delete()
            }
            if (!target.exists()) throw FileNotFoundException("Failed to rename $documentId")
        }
        notifyChanged(documentId, target)
        return getDocIdForFile(target)
    }

    @Throws(FileNotFoundException::class)
    override fun copyDocument(sourceDocumentId: String, targetParentDocumentId: String): String {
        val src = getFileForDocId(sourceDocumentId)
        val parent = getFileForDocId(targetParentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException("Target parent is not a dir")
        var dest = File(parent, src.name)
        var n = 2
        while (dest.exists()) {
            dest = File(parent, "${src.name} ($n)")
            n++
        }
        if (src.isDirectory) {
            src.copyRecursively(dest, overwrite = false)
        } else {
            src.copyTo(dest, overwrite = false)
        }
        notifyChanged(targetParentDocumentId, dest)
        return getDocIdForFile(dest)
    }

    @Throws(FileNotFoundException::class)
    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String
    ): String {
        val src = getFileForDocId(sourceDocumentId)
        val parent = getFileForDocId(targetParentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException("Target parent is not a dir")
        var dest = File(parent, src.name)
        var n = 2
        while (dest.exists()) {
            dest = File(parent, "${src.name} ($n)")
            n++
        }
        // Try fast rename first, fall back to copy+delete.
        val renamed = try { src.renameTo(dest) } catch (e: Exception) { false }
        if (!renamed && !dest.exists()) {
            if (src.isDirectory) {
                src.copyRecursively(dest, overwrite = false)
                src.deleteRecursively()
            } else {
                src.copyTo(dest, overwrite = false)
                src.delete()
            }
        }
        if (!dest.exists()) throw FileNotFoundException("Failed to move $sourceDocumentId")
        notifyChanged(sourceDocumentId)
        notifyChanged(targetParentDocumentId, dest)
        return getDocIdForFile(dest)
    }

    private fun authority(): String {
        val ctx = context
        return if (ctx != null) "${ctx.packageName}.documents" else "com.wolfi.terminal.documents"
    }

    private fun notifyChanged(vararg filesOrIds: Any) {
        val ctx = context ?: return
        val resolver = ctx.contentResolver ?: return
        val auth = authority()
        // Notify roots (free-space/titles) once.
        runCatching {
            resolver.notifyChange(
                DocumentsContract.buildRootsUri(auth),
                null
            )
        }
        for (item in filesOrIds) {
            val f: File? = when (item) {
                is File -> item
                is String -> runCatching { File(item) }.getOrNull()
                else -> null
            }
            f ?: continue
            runCatching {
                resolver.notifyChange(
                    DocumentsContract.buildDocumentUri(
                        auth, f.absolutePath
                    ), null
                )
            }
            runCatching {
                f.parentFile?.let { p ->
                    resolver.notifyChange(
                        DocumentsContract.buildChildDocumentsUri(
                            auth, p.absolutePath
                        ), null
                    )
                }
            }
        }
    }

    @Throws(FileNotFoundException::class)
    private fun includeFile(result: MatrixCursor, docId: String?, file: File?) {
        val finalDocId = docId ?: getDocIdForFile(file!!)
        val finalFile = file ?: getFileForDocId(finalDocId)

        var flags = 0
        if (finalFile.isDirectory) {
            if (finalFile.canWrite()) flags = flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
        } else if (finalFile.canWrite()) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
        }
        if (finalFile.parentFile?.canWrite() == true) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                DocumentsContract.Document.FLAG_SUPPORTS_RENAME or
                DocumentsContract.Document.FLAG_SUPPORTS_COPY or
                DocumentsContract.Document.FLAG_SUPPORTS_MOVE
        }

        val displayName = finalFile.name
        val mimeType = getMimeType(finalFile)
        if (mimeType.startsWith("image/")) flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL

        val row = result.newRow()
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, finalDocId)
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
        row.add(DocumentsContract.Document.COLUMN_SIZE, finalFile.length())
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType)
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, finalFile.lastModified())
        row.add(DocumentsContract.Document.COLUMN_FLAGS, flags)
        row.add(DocumentsContract.Document.COLUMN_ICON, R.mipmap.ic_launcher)
    }

    companion object {
        fun isDocumentProviderEnabled(context: Context): Boolean {
            val componentName = ComponentName(context, AlpineDocumentProvider::class.java)
            val state = context.packageManager.getComponentEnabledSetting(componentName)
            return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                    state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }

        fun setDocumentProviderEnabled(context: Context, enabled: Boolean) {
            if (isDocumentProviderEnabled(context) == enabled) return
            val componentName = ComponentName(context, AlpineDocumentProvider::class.java)
            val newState = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            context.packageManager.setComponentEnabledSetting(componentName, newState, PackageManager.DONT_KILL_APP)
        }

        private const val ALL_MIME_TYPES = "*/*"
        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
        )
        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )

        private fun getDocIdForFile(file: File): String = file.absolutePath

        @Throws(FileNotFoundException::class)
        private fun getFileForDocId(docId: String): File {
            val f = File(docId)
            if (!f.exists()) throw FileNotFoundException(f.absolutePath + " not found")
            return f
        }

        private fun getMimeType(file: File): String {
            if (file.isDirectory) return DocumentsContract.Document.MIME_TYPE_DIR
            val name = file.name
            val lastDot = name.lastIndexOf('.')
            if (lastDot >= 0) {
                val extension = name.substring(lastDot + 1).lowercase(Locale.getDefault())
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                if (mime != null) return mime
            }
            return "application/octet-stream"
        }
    }
}
