package io.github.alcindogandhi.champs.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.resumeWithException

/** Resposta da ESPN, vinda da rede ou do cache local. */
class Body(val text: String, val fetchedAt: Long)

class HttpException(val code: Int, url: String) : IOException("HTTP $code em $url")

/**
 * Busca JSON na API pública da ESPN e guarda cada resposta em arquivo (`files/espn/`), para o app abrir
 * na hora com o último dado conhecido e funcionar sem internet.
 */
class EspnClient(
    private val http: OkHttpClient,
    private val cacheDir: File,
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun currentTime(): Long = now()

    suspend fun cached(url: String): Body? = withContext(Dispatchers.IO) {
        val file = fileFor(url)
        if (!file.exists()) return@withContext null
        runCatching { Body(file.readText(), file.lastModified()) }.getOrNull()
    }

    suspend fun fetch(url: String): Body = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()
        val text = http.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code, url)
            response.body.string()
        }
        val fetchedAt = now()
        val file = fileFor(url)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        tmp.setLastModified(fetchedAt)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        file.setLastModified(fetchedAt)
        Body(text, fetchedAt)
    }

    suspend fun clear() = withContext(Dispatchers.IO) { cacheDir.deleteRecursively() }

    private fun fileFor(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$name.json")
    }

    companion object {
        const val SITE = "https://site.api.espn.com/apis/site/v2/sports/soccer"
        const val STANDINGS = "https://site.api.espn.com/apis/v2/sports/soccer"

        /**
         * Idioma dos textos da ESPN (narração, status, observações), conforme o idioma do app:
         * português do Brasil ou, para os demais, inglês.
         */
        fun localeQuery(locale: Locale = Locale.getDefault()): String =
            if (locale.language == "pt") "lang=pt&region=br" else "lang=en&region=us"
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { _, value, _ -> value.close() }
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
