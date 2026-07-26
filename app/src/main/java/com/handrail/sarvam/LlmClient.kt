package com.handrail.sarvam

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class LlmClient {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private companion object {
        const val ATTEMPT_TIMEOUT_MS = 20_000L
    }

    suspend fun chatCompletion(
        messages: List<ChatMessage>,
        maxRetries: Int = 2,
    ): Result<ChatCompletionResponse> = withContext(Dispatchers.IO) {
        val bodyJson = json.encodeToString(
            ChatCompletionRequest(model = LlmConfig.model, messages = messages),
        )
        val url = LlmConfig.baseUrl.trimEnd('/') + "/chat/completions"

        var lastError: Throwable? = null
        repeat(maxRetries + 1) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer ${LlmConfig.apiKey}")
                    .addHeader("Content-Type", "application/json")
                    .post(bodyJson.toRequestBody(jsonMediaType))
                    .build()

                // A plain blocking call() is not a real suspension point, so
                // wrapping it in withTimeoutOrNull is a no-op — cancellation
                // is cooperative and can't preempt code that never yields
                // back to the coroutine machinery. That was observed letting
                // a slow response wedge the agent loop indefinitely on a
                // large, repetitive perceived screen (a Google News results
                // page) despite a "timeout" being in place. executeAsync()
                // uses OkHttp's real async API so a timeout can actually
                // call Call.cancel(), which unblocks the underlying socket.
                val responseBody = withTimeoutOrNull(ATTEMPT_TIMEOUT_MS) {
                    executeAsync(request).use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            throw IOException("LLM call failed: HTTP ${resp.code} - $body")
                        }
                        body
                    }
                }
                if (responseBody != null) {
                    return@withContext Result.success(json.decodeFromString(responseBody))
                }
                lastError = IOException("LLM call timed out after ${ATTEMPT_TIMEOUT_MS}ms")
            } catch (e: Exception) {
                lastError = e
            }
        }
        Result.failure(lastError ?: IOException("Unknown LLM client error"))
    }

    private suspend fun executeAsync(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response)
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isCancelled) return
                        continuation.resumeWithException(e)
                    }
                },
            )
        }

    suspend fun smokeTest(): Result<String> {
        val result = chatCompletion(
            messages = listOf(ChatMessage(role = "user", content = "Reply with exactly: pong")),
        )
        return result.map { response ->
            response.choices.firstOrNull()?.message?.content ?: "(empty response)"
        }
    }
}
