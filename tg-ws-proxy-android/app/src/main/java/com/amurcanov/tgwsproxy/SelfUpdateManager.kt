package com.amurcanov.tgwsproxy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object SelfUpdateManager {

    private const val TAG = "SelfUpdate"
    private const val DOWNLOAD_BUFFER_BYTES = 64 * 1024

    fun updateApkFile(context: Context): File {
        val dir = context.getExternalFilesDir("update") ?: File(context.filesDir, "update")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "update.apk")
    }

    fun deleteStaleApk(context: Context) {
        try {
            updateApkFile(context).delete()
        } catch (e: Exception) {
            Log.w(TAG, "[WARN] Failed to delete stale update apk", e)
        }
    }

    suspend fun downloadApk(
        context: Context,
        release: AppReleaseInfo,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val apkUrl = release.apkUrl
            ?: throw IllegalStateException("Release ${release.versionTag} has no apk asset")

        val outputFile = updateApkFile(context)
        if (outputFile.exists()) {
            outputFile.delete()
        }
        val tempFile = File(outputFile.parentFile, "update.apk.part")

        var conn: HttpURLConnection? = null
        try {
            conn = URL(apkUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.android.package-archive,*/*")
            conn.setRequestProperty("User-Agent", "TGWSProxyAndroid/${BuildConfig.VERSION_NAME}")
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("APK download failed with HTTP $responseCode")
            }

            val totalBytes = conn.contentLengthLong.takeIf { it > 0L }
                ?: release.apkSizeBytes?.takeIf { it > 0L }
                ?: -1L

            conn.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    var downloaded = 0L
                    var lastReportedProgress = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0) {
                            val progress = ((downloaded * 100L) / totalBytes).toInt()
                            if (progress != lastReportedProgress) {
                                lastReportedProgress = progress
                                onProgress(progress.coerceIn(0, 100) / 100f)
                            }
                        }
                    }
                }
            }

            if (totalBytes > 0 && tempFile.length() != totalBytes) {
                throw IllegalStateException(
                    "APK download incomplete: expected $totalBytes bytes, got ${tempFile.length()}"
                )
            }

            verifySha256IfPresent(tempFile, release.apkSha256)

            if (outputFile.exists()) {
                outputFile.delete()
            }
            if (!tempFile.renameTo(outputFile)) {
                throw IllegalStateException("Failed to finalize downloaded apk")
            }
            outputFile
        } catch (e: Exception) {
            try {
                tempFile.delete()
            } catch (cleanup: Exception) {
                Log.w(TAG, "[WARN] Failed to clean up partial apk", cleanup)
            }
            Log.w(TAG, "[WARN] APK download failed", e)
            throw e
        } finally {
            conn?.disconnect()
        }
    }

    fun buildInstallIntent(context: Context, apkFile: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun verifySha256IfPresent(file: File, expectedSha256: String?) {
        val expected = expectedSha256?.trim()?.lowercase() ?: return
        if (expected.isEmpty()) return

        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(expected, ignoreCase = true)) {
            throw IllegalStateException("APK sha256 mismatch: expected=$expected actual=$actual")
        }
    }
}
