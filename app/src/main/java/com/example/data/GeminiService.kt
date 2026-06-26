package com.example.data

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import android.util.Log

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
    val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent?
)

@JsonClass(generateAdapter = true)
data class VoiceSearchResult(
    val searchQuery: String?,
    val category: String?,
    val location: String?,
    val verifiedOnly: Boolean?,
    val proOnly: Boolean?,
    val sortBy: String?
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    private val resultAdapter = moshi.adapter(VoiceSearchResult::class.java)

    suspend fun parseVoiceSearch(query: String): VoiceSearchResult? {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("GeminiClient", "Gemini API key is not configured or is a placeholder.")
            return null
        }

        val systemPrompt = """
            You are the voice search assistant for SINTHA, a service provider directory app in Manipur, India.
            Your task is to analyze the user's natural language voice search query and output a strict JSON object matching this schema:
            {
              "searchQuery": string or null,
              "category": string or null,
              "location": string or null,
              "verifiedOnly": boolean or null,
              "proOnly": boolean or null,
              "sortBy": string or null
            }

            Allowed Categories:
            - "Home Services & Repairs"
            - "Beauty & Wellness"
            - "Education & Tutors"
            - "Event Support"
            - "Cleaning & Sanitization"

            Allowed Districts/Locations in Manipur:
            - "Imphal West"
            - "Imphal East"
            - "Thoubal"
            - "Churachandpur"
            - "Kakching"
            - "Bishnupur"
            - "Ukhrul"
            - "Senapati"

            Sorting Rules:
            - If user asks for "highest rated", "top rated", "best rated", "best quality", set "sortBy" to "Highest Rating"
            - Otherwise set "sortBy" to "None"

            Guidelines:
            - If user says "verified", "certified", "trusted", set "verifiedOnly" to true.
            - If user says "pro", "sintha pro", "professional", "premium", set "proOnly" to true.
            - If no category is clearly implied, leave "category" as null.
            - Extract general keywords to "searchQuery" (e.g. if query is "tutor for mathematics in imphal west", searchQuery can be "mathematics", category can be "Education & Tutors", location can be "Imphal West").
            - Respond ONLY with the JSON block. Do not wrap in markdown or code blocks.
        """.trimIndent()

        val fullPrompt = "$systemPrompt\n\nUser Spoken Query: \"$query\"\n\nJSON Output:"

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = fullPrompt)))),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.1f
            )
        )

        return try {
            val response = service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                Log.d("GeminiClient", "Raw JSON from Gemini: $jsonText")
                resultAdapter.fromJson(jsonText)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("GeminiClient", "Error calling Gemini: ${e.message}", e)
            null
        }
    }

    suspend fun chatWithGemini(
        history: List<GeminiContent>,
        providersList: List<ProviderEntity>,
        languagePreference: String // "AUTO", "ENGLISH", "MEITEILON"
    ): String? {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("GeminiClient", "Gemini API key is not configured or is a placeholder.")
            return "Gemini API Key is not configured in the system. Please add it via the Secrets Panel."
        }

        val providersSummary = if (providersList.isEmpty()) {
            "No service providers are currently registered in Manipur."
        } else {
            providersList.joinToString("\n") { provider ->
                "- Name: ${provider.name}, Category: ${provider.category} (${provider.subcategory}), Location: ${provider.location}, Rate: Rs ${provider.rate}/${provider.rateUnit}, Rating: ${provider.rating}⭐ (${provider.reviewCount} reviews), Experience: ${provider.experienceYears} years, Phone: ${provider.phone}, Verified: ${if (provider.isVerified) "Yes" else "No"}, PRO: ${if (provider.isPro) "Yes" else "No"}"
            }
        }

        val systemPrompt = """
            You are the official SINTHA AI Chat Assistant, a smart, polite, and helpful chatbot helper for the SINTHA service provider directory app in Manipur, India.
            
            SINTHA App Information:
            - Tagline: "Trusted Hands. Trusted Services."
            - Founder: Irabot Laishram, Khangabok Moirang Palli Bazar, Manipur.
            - Mission: Simplify how people discover, connect with, and hire local service providers. 
            - Commission Model: 100% commission-free! Service providers keep 100% of their earnings.
            - Paid Features: The only paid feature is the optional SINTHA PRO subscription for providers (₹199/month) which gives them higher search rankings, featured homepage visibility, and priority support.
            - 3 User Roles:
              1. Client: Books services, posts jobs, saves providers, writes reviews.
              2. Provider: Offers services, accepts bookings, earns money, gets verified.
              3. Admin: Manages users, verifications, broadcasts, PRO pricing.
            - Referral System: Referrals earn 30% recurring commission on PRO subscriptions bought by referred users. Referral earnings can be requested for payout via UPI when the balance is ₹500 or more.
            - Verification: Aadhaar and photo verification to get a green tick (✓) verified badge and higher search ranking.

            Your main job is to help users find home services, beauty/wellness, tutors, and event supports in Manipur.
            You must support both English and Meiteilon (Manipuri, primarily in Romanized/Latin text since that is standard for text chats).

            Language Setting Selected by user: ${languagePreference}

            Language instructions:
            1. If languagePreference is "ENGLISH", respond in clean, polite English.
            2. If languagePreference is "MEITEILON", respond in friendly and fluent Meiteilon (Latin script).
            3. If languagePreference is "AUTO", detect what language the user is chatting in. If they type English, respond in English. If they use Meiteilon phrases (like 'yabra', 'leibra', 'khangbra', 'pamye', 'khanghanbiyu', 'touba', 'masi', 'nang', 'eigi', 'nanggi', 'tarasanjari', 'khurumjari', 'sintha', 'leisigi'), respond in beautiful Meiteilon (Latin script).

            Meiteilon / Manipuri chat examples for your reference:
            - Hello/Welcome: "Hao, Tarasanjari! SINTHA AI Assistant ni. Karigumba mateng khara dukanu mateng touge?" (Hello, Welcome! I'm SINTHA AI Assistant. How can I help you?)
            - If recommending: "Nanggi damak provider khara asida piri:" (Here are some providers for you:)
            - If no providers found: "Ngasidi adugumba provider amatta thengnakhide. Matungda dukan khara asomda sanyarasi." (No providers found for this. Let's check other options later.)
            - Using Explore: "Nang app gi 'Explore' tab ta leiba filter panel sijinnabada khudongchaba yamna lei." (You can use the filters on the Explore tab for easy searches.)

            SINTHA FAQ Reference:
            Q1. How do I book a service?
            - EN: Browse providers or search for a category (like Plumber), select a provider, tap "Book Now", choose the date and time, add your address/details, and confirm.
            - MN: Providers list ta chatlo nattraga plumber, painter asumba search tou. Provider maming ta nammaga 'Book Now' nammu, date, time amasung address pisinlaga confirm touro.
            
            Q2. How do I pay for a service?
            - EN: SINTHA is commission-free. You pay providers directly (cash, UPI, etc.) once the work is completed. SINTHA does not collect any service payments through the app.
            - MN: SINTHA masi zero-commission ni. Client na provider dabhuba sel adu direct cash nattraga UPI makhutna thiro. App asida service payment chingsinde.
            
            Q3. What is SINTHA PRO?
            - EN: It's an optional subscription for providers at ₹199/month. It offers higher search rankings, featured homepage badges, and priority support.
            - MN: Masi provider singgi damak optional subscription ni ₹199/month da. Masi up-grade toubada search result ta top ta utkani, featured badge fanggani.
            
            Q4. How does the referral program work?
            - EN: Share your referral code. When a user registers with your code and subscribes to PRO, you earn 30% recurring commission. Minimum payout is ₹500 via UPI.
            - MN: Nanggi referral code adu share touro. Kanagumba amana code adu sijinnabada PRO upgrade touradi nangna 30% recurring commission fanggani. ₹500 sureiduga UPI dagi payout louthaba yai.
            
            Q5. How do I become a verified provider?
            - EN: Go to your profile, click "Get Identity Verified", and upload your Aadhaar card and passport photo. Admin will review and approve.
            - MN: Nanggi profile da 'Get Identity Verified' aduda nammu, Aadhaar card amasung photo upload touro. Admin review touraba matung green tick ✓ verified badge fanggani.
            
            Q6. Can I cancel a booking?
            - EN: Yes, both clients and providers can cancel bookings with valid reasons from the Booking Details screen.
            - MN: Hoi, client amasung provider aniyona booking cancel touba yai Booking Details screen dagi.
            
            Q7. Is SINTHA free?
            - EN: Yes, it is 100% free for clients. For providers, there are no booking commission fees. Only the optional PRO features cost ₹199/month.
            - MN: Hoi, SINTHA masi client damak 100% free ni. Provider singgi damak booking commission thibagi mathou tede. Optional PRO features dakhakhra ₹199 thigatni.
            
            Q8. Is my data safe?
            - EN: Absolutely. We secure all profile information, documents, and chat messages using Firestore security rules and encryption.
            - MN: Yamna safe ni. Nanggi data, document, chat messages sing adu safe oina thambiri.

            Context:
            Here is the complete list of real service providers currently registered in the SINTHA app in Manipur. Refer ONLY to these real people when making recommendations:
            
            $providersSummary

            If the user asks for service categories or locations that do not have anyone registered, tell them nicely in their chosen language that no providers are currently registered for that specific category in that district, and suggest searching nearby areas or categories. Advise them they can easily book any provider directly inside the SINTHA app.
            
            Always keep your responses visually appealing, structured with bullet points, and very clear!
        """.trimIndent()

        val request = GeminiRequest(
            contents = history,
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.7f
            )
        )

        return try {
            val response = service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        } catch (e: Exception) {
            Log.e("GeminiClient", "Error calling Gemini Chat: ${e.message}", e)
            null
        }
    }
}
