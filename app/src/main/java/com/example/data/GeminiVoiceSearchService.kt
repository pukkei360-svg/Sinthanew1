package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object GeminiVoiceSearchService {
    private const val TAG = "GeminiVoiceSearch"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class VoiceSearchFilters(
        val searchQuery: String = "",
        val category: String? = null,
        val location: String? = null,
        val verifiedOnly: Boolean = false,
        val proOnly: Boolean = false
    )

    // Parse prompt to Gemini and extract search parameters
    suspend fun analyzeVoiceSearch(spokenText: String): VoiceSearchFilters {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is not configured in .env or Secrets panel.")
            // Graceful fallback to client-side parsing!
            return parseFallback(spokenText)
        }

        val prompt = """
            You are the SINTHA AI Voice Search Assistant. SINTHA is a service provider booking app based in Imphal, Manipur, India.
            The user spoke this search query: "$spokenText"
            
            Based on the query, extract search parameters into a JSON object matching this structure:
            {
              "searchQuery": "cleaned search term (e.g., 'electrician', 'math tutor', 'bridal makeup', 'camera' or empty string)",
              "category": "exact category name matching one of the allowed categories, or null",
              "location": "exact location name matching one of the allowed locations, or null",
              "verifiedOnly": true/false (if user requested 'verified', 'certified', 'trusted' providers),
              "proOnly": true/false (if user requested 'pro', 'expert', 'best', 'top-rated', 'premium' providers)
            }
            
            Allowed Categories:
            - "Home Services & Repairs"
            - "Beauty & Wellness"
            - "Tutoring & Lessons"
            - "Events & Photography"
            - "IT & Creative"
            - "Health & Care"
            
            Allowed Locations in Manipur:
            - "Imphal West"
            - "Imphal East"
            - "Thoubal"
            - "Kakching"
            - "Bishnupur"
            - "Ukhrul"
            - "Churachandpur"
            - "Senapati"
            
            Ensure the fields 'category' and 'location' EXACTLY match the allowed options or are null.
            Return ONLY the raw JSON object, do not wrap in markdown or backticks.
        """.trimIndent()

        val jsonRequest = """
            {
              "contents": [
                {
                  "parts": [
                    {
                      "text": ${escapeJsonString(prompt)}
                    }
                  ]
                }
              ],
              "generationConfig": {
                "responseMimeType": "application/json",
                "temperature": 0.1
              }
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("$BASE_URL?key=$apiKey")
            .post(jsonRequest.toRequestBody("application/json".toMediaType()))
            .build()

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Gemini API call failed with code: ${response.code}")
                        return@withContext parseFallback(spokenText)
                    }

                    val bodyString = response.body?.string() ?: return@withContext parseFallback(spokenText)
                    Log.d(TAG, "Gemini Raw Response: $bodyString")

                    // Parse the response candidates
                    val responseAdapter = moshi.adapter(Map::class.java)
                    val responseMap = responseAdapter.fromJson(bodyString) ?: return@withContext parseFallback(spokenText)

                    val candidates = responseMap["candidates"] as? List<*>
                    val candidate = candidates?.firstOrNull() as? Map<*, *>
                    val content = candidate?.get("content") as? Map<*, *>
                    val parts = content?.get("parts") as? List<*>
                    val part = parts?.firstOrNull() as? Map<*, *>
                    val textResult = part?.get("text") as? String ?: ""

                    Log.d(TAG, "Gemini Extracted JSON: $textResult")

                    // Parse the inner JSON
                    val filterAdapter = moshi.adapter(VoiceSearchFilters::class.java)
                    filterAdapter.fromJson(textResult) ?: parseFallback(spokenText)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error calling Gemini API: ${e.message}", e)
                parseFallback(spokenText)
            }
        }
    }

    // Helper to escape json strings
    private fun escapeJsonString(input: String): String {
        return moshi.adapter(String::class.java).toJson(input)
    }

    // Simple heuristic-based local fallback in case of no network, API limit, or empty API key
    private fun parseFallback(text: String): VoiceSearchFilters {
        val lower = text.lowercase()
        var category: String? = null
        var location: String? = null
        var verifiedOnly = false
        var proOnly = false

        // Heuristics for categories
        if (lower.contains("electric") || lower.contains("plumber") || lower.contains("carpenter") || lower.contains("clean") || lower.contains("repair") || lower.contains("home service") || lower.contains("washing")) {
            category = "Home Services & Repairs"
        } else if (lower.contains("tutor") || lower.contains("lesson") || lower.contains("teacher") || lower.contains("math") || lower.contains("science") || lower.contains("class") || lower.contains("study") || lower.contains("tuition")) {
            category = "Tutoring & Lessons"
        } else if (lower.contains("beauty") || lower.contains("salon") || lower.contains("makeup") || lower.contains("spa") || lower.contains("hair") || lower.contains("massage") || lower.contains("facial")) {
            category = "Beauty & Wellness"
        } else if (lower.contains("photo") || lower.contains("video") || lower.contains("shoot") || lower.contains("camera") || lower.contains("event") || lower.contains("wedding")) {
            category = "Events & Photography"
        } else if (lower.contains("web") || lower.contains("app") || lower.contains("logo") || lower.contains("design") || lower.contains("it ") || lower.contains("developer") || lower.contains("coder") || lower.contains("software")) {
            category = "IT & Creative"
        } else if (lower.contains("nurse") || lower.contains("elder") || lower.contains("physio") || lower.contains("doctor") || lower.contains("care") || lower.contains("health") || lower.contains("clinic")) {
            category = "Health & Care"
        }

        // Heuristics for locations
        if (lower.contains("imphal west") || lower.contains("west imphal")) {
            location = "Imphal West"
        } else if (lower.contains("imphal east") || lower.contains("east imphal")) {
            location = "Imphal East"
        } else if (lower.contains("thoubal")) {
            location = "Thoubal"
        } else if (lower.contains("kakching")) {
            location = "Kakching"
        } else if (lower.contains("bishnupur")) {
            location = "Bishnupur"
        } else if (lower.contains("ukhrul")) {
            location = "Ukhrul"
        } else if (lower.contains("churachandpur") || lower.contains("ccpur")) {
            location = "Churachandpur"
        } else if (lower.contains("senapati")) {
            location = "Senapati"
        }

        // Flags
        if (lower.contains("verify") || lower.contains("trust") || lower.contains("certif")) {
            verifiedOnly = true
        }
        if (lower.contains("pro") || lower.contains("best") || lower.contains("expert") || lower.contains("top") || lower.contains("featured") || lower.contains("premium")) {
            proOnly = true
        }

        // Clean up search query by removing location and meta-words
        var cleanedQuery = text
            .replace("imphal west", "", ignoreCase = true)
            .replace("imphal east", "", ignoreCase = true)
            .replace("thoubal", "", ignoreCase = true)
            .replace("kakching", "", ignoreCase = true)
            .replace("bishnupur", "", ignoreCase = true)
            .replace("ukhrul", "", ignoreCase = true)
            .replace("churachandpur", "", ignoreCase = true)
            .replace("senapati", "", ignoreCase = true)
            .replace("find a", "", ignoreCase = true)
            .replace("find", "", ignoreCase = true)
            .replace("search for", "", ignoreCase = true)
            .replace("search", "", ignoreCase = true)
            .replace("verified", "", ignoreCase = true)
            .replace("pro", "", ignoreCase = true)
            .replace("best", "", ignoreCase = true)
            .replace("expert", "", ignoreCase = true)
            .replace("top rated", "", ignoreCase = true)
            .replace("looking for", "", ignoreCase = true)
            .replace("need a", "", ignoreCase = true)
            .replace("need", "", ignoreCase = true)
            .replace("\\s+".toRegex(), " ")
            .trim()

        if (cleanedQuery.lowercase() == "in" || cleanedQuery.lowercase() == "at" || cleanedQuery.lowercase() == "for") {
            cleanedQuery = ""
        }

        return VoiceSearchFilters(
            searchQuery = cleanedQuery,
            category = category,
            location = location,
            verifiedOnly = verifiedOnly,
            proOnly = proOnly
        )
    }
}
