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
import java.net.URI
import java.util.concurrent.TimeUnit

class DigestApiClient(
    private val json: Json,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build()
) {

    suspend fun fetchLatestDigest(baseUrl: String, githubToken: String = ""): DailyDigest = withContext(Dispatchers.IO) {
        val cleanBase = FeedDefaults.normalizeBaseUrl(baseUrl)
        require(cleanBase.isNotEmpty()) { "Feed URL is not configured." }
        val body = getString("${cleanBase}daily_digest.json", githubToken)
        json.decodeFromString<DailyDigest>(body).enforcedLimit()
    }

    suspend fun fetchManifest(baseUrl: String, githubToken: String = ""): DigestManifest = withContext(Dispatchers.IO) {
        val cleanBase = FeedDefaults.normalizeBaseUrl(baseUrl)
        require(cleanBase.isNotEmpty()) { "Feed URL is not configured." }
        val body = getString("${cleanBase}manifest.json", githubToken)
        json.decodeFromString<DigestManifest>(body)
    }

    suspend fun fetchEdition(
        baseUrl: String,
        relativePath: String,
        githubToken: String = "",
    ): DailyDigest = withContext(Dispatchers.IO) {
        val cleanBase = FeedDefaults.normalizeBaseUrl(baseUrl)
        require(cleanBase.isNotEmpty()) { "Feed URL is not configured." }
        val cleanPath = relativePath.trimStart('/')
        val body = getString("$cleanBase$cleanPath", githubToken)
        json.decodeFromString<DailyDigest>(body).enforcedLimit()
    }

    private fun isGitHubHost(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull().orEmpty()
        return host == "raw.githubusercontent.com" || host == "api.github.com"
    }

    private fun getString(url: String, githubToken: String): String {
        val cleanToken = githubToken.trim()
        val firstCode = executeGet(url, cleanToken, useApiAccept = false)
        if (firstCode.isSuccess) return firstCode.getOrThrow()

        // If private raw URL returned 401/403/404 and token is present, fall back to GitHub REST Contents API.
        val apiFallbackUrl = if (cleanToken.isNotEmpty()) FeedDefaults.toGitHubApiRawUrl(url) else null
        if (apiFallbackUrl != null) {
            val apiResult = executeGet(apiFallbackUrl, cleanToken, useApiAccept = true)
            if (apiResult.isSuccess) return apiResult.getOrThrow()
            throw apiResult.exceptionOrNull()!!
        }
        throw firstCode.exceptionOrNull()!!
    }

    private fun executeGet(url: String, token: String, useApiAccept: Boolean): Result<String> {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", if (useApiAccept) "application/vnd.github.raw+json" else "application/json")
            .header("Cache-Control", "no-cache")

        if (token.isNotEmpty() && isGitHubHost(url)) {
            builder.header("Authorization", "Bearer $token")
            builder.header("X-GitHub-Api-Version", "2022-11-28")
        }

        return runCatching {
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} when fetching $url")
                }
                response.body?.string() ?: throw IOException("Empty response from $url")
            }
        }
    }
}
