package com.capri.mobile.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class BrainDown(message: String) : Exception(message)

/** Minimal OpenAI-compatible client. Works with OpenAI, OpenRouter, Groq, Ollama, LM Studio and llama.cpp server. */
class LlmClient {

    data class Config(
        val baseUrl: String,
        val model: String,
        val apiKey: String,
        val temperature: Float,
        val maxTokens: Int
    )

    fun chat(config: Config, messages: List<ChatMessage>, temperature: Float? = null, maxTokens: Int? = null): String {
        val endpoint = endpoint(config.baseUrl)
        val body = JSONObject().apply {
            put("model", config.model)
            put("temperature", temperature ?: config.temperature)
            put("max_tokens", maxTokens ?: config.maxTokens)
            put("stream", false)
            put("messages", JSONArray().apply {
                messages.forEach { m ->
                    put(JSONObject().apply {
                        put("role", m.role)
                        put("content", m.content)
                    })
                }
            })
        }
        val raw = post(endpoint, body.toString(), config.apiKey)
        val root = JSONObject(raw)
        val choices = root.optJSONArray("choices")
            ?: throw BrainDown("Endpoint answered, but there's no 'choices' in it. Is that an OpenAI-compatible URL?")
        if (choices.length() == 0) throw BrainDown("The model sent an empty choice list.")
        val message = choices.optJSONObject(0)?.optJSONObject("message")
        var text = message?.optString("content").orEmpty()
        if (text.isBlank()) text = choices.optJSONObject(0)?.optString("text").orEmpty()
        text = text.replace(Regex("(?is)<think\\b[^>]*>.*?</think\\s*>"), "").trim()
        if (text.isBlank()) throw BrainDown("The model replied with nothing. Try again or pick another model.")
        return text
    }

    fun summarize(config: Config, transcript: String): String {
        val msgs = listOf(
            ChatMessage("user", "Summarize this conversation between Capri and her user in one short line. Keep every " +
                "name, preference, plan and event worth remembering. Plain text, no lists.\n\n$transcript")
        )
        return chat(config, msgs, temperature = 0.2f, maxTokens = 90)
    }

    private fun endpoint(base: String): String {
        var b = base.trim()
        if (b.isBlank()) throw BrainDown("No AI endpoint configured.")
        if (!b.startsWith("http://") && !b.startsWith("https://")) b = "https://$b"
        b = b.trimEnd('/')
        return if (b.endsWith("/chat/completions")) b else "$b/chat/completions"
    }

    private fun post(url: String, body: String, apiKey: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { s ->
                BufferedReader(InputStreamReader(s, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (code !in 200..299) {
                val friendly = when (code) {
                    401, 403 -> "The endpoint rejected the API key ($code)."
                    404 -> "Endpoint not found (404). Check the base URL and model."
                    429 -> "Rate limited (429). Give it a minute, choom."
                    in 500..599 -> "The model server is down ($code)."
                    else -> "The AI endpoint returned HTTP $code."
                }
                val detail = text.take(400).replace(Regex("\\s+"), " ")
                throw BrainDown(if (detail.isNotBlank()) "$friendly $detail" else friendly)
            }
            return text
        } catch (e: BrainDown) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            throw BrainDown("The model took too long to answer. Timeout.")
        } catch (e: java.net.UnknownHostException) {
            throw BrainDown("Can't reach that host. Check the URL and your connection.")
        } catch (e: java.net.ConnectException) {
            throw BrainDown("Connection refused. Is the local model server running?")
        } catch (e: Exception) {
            throw BrainDown("Brain link failed: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            conn.disconnect()
        }
    }
}
