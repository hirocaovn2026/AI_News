package com.ainews.globalbrief.data.remote

import com.ainews.globalbrief.data.model.DailyDigest
import com.ainews.globalbrief.data.model.DigestManifest
import com.ainews.globalbrief.data.model.FeedDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class DigestApiClient(
    private val json: Json,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build()
) {

    suspend fun fetchLatestDigest(baseUrl: String): DailyDigest = withContext(Dispatchers.IO) {
        val cleanBase = FeedDefaults.normalizeBaseUrl(baseUrl)
        require(cleanBase.isNotEmpty()) { "Feed URL is not configured." }
        val body = getString("${cleanBase}daily_digest.json")
        json.decodeFromString<DailyDigest>(body).enforcedLimit()
    }

    suspend fun fetchManifest(baseUrl: String): DigestManifest = withContext(Dispatchers.IO) {
        val cleanBase = FeedDefaults.normalizeBaseUrl(baseUrl)
        require(cleanBase.isNotEmpty()) { "Feed URL is not configured." }
        val body = getString("${cleanBase}manifest.json")
        json.decodeFromString<DigestManifest>(body)
    }

    suspend fun fetchEdition(baseUrl: String, relativePath: String): DailyDigest = withContext(Dispatchers.IO) {
        val cleanBase = FeedDefaults.normalizeBaseUrl(baseUrl)
        require(cleanBase.isNotEmpty()) { "Feed URL is not configured." }
        val cleanPath = relativePath.trimStart('/')
        val body = getString("$cleanBase$cleanPath")
        json.decodeFromString<DailyDigest>(body).enforcedLimit()
    }

    private fun getString(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} when fetching $url")
            }
            return response.body?.string() ?: throw IOException("Empty response from $url")
        }
    }
}
