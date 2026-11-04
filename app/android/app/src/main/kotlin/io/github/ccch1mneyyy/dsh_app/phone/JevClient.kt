package io.github.ccch1mneyyy.dsh_app.phone

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class JevChoice(val value: String, val probability: Double, val margin: Double)

object JevClient {
    private const val TIMEOUT_MS = 8_000
    private const val QUESTION = "next_step"

    fun decide(config: JevConfig, state: String, instruction: String, candidates: Map<String, String>): JevChoice? {
        if (!config.isUsable || candidates.isEmpty()) return null
        return try {
            val body = buildRequest(state, instruction, candidates)
            val response = post(config.apiUrl, config.apiKey, body)
            parseResponse(response)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildRequest(state: String, instruction: String, candidates: Map<String, String>): String {
        val criteria = JSONObject()
        for ((k, v) in candidates) criteria.put(k, v)
        val question = JSONObject().apply {
            put("type", "choice"); put("instructions", instruction); put("criteria", criteria)
        }
        return JSONObject().apply {
            put("model", "jev-latest")
            put("state", state)
            put("questions", JSONObject().apply { put(QUESTION, question) })
        }.toString()
    }

    private fun post(url: String, apiKey: String, body: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", apiKey)
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.doOutput = true
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
        return BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun parseResponse(text: String): JevChoice? {
        val q = JSONObject(text).optJSONObject(QUESTION) ?: return null
        val value = q.optString("value").takeIf { it.isNotBlank() } ?: return null
        val prob = q.optDouble("probability", q.optDouble("confidence", Double.NaN))
        val margin = q.optDouble("margin", Double.NaN)
        if (prob.isNaN() || margin.isNaN()) return null
        return JevChoice(value, prob, margin)
    }
}
