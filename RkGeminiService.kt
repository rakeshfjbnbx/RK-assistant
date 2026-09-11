package com.example

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GeminiParsedResult(
    val spokenResponse: String,
    val detectedLanguage: String, // "bn", "hi", "en"
    val actionType: ActionType,
    val actionTarget: String? = null,
    val searchQuery: String? = null,
    val phoneNumber: String? = null,
    val smsBody: String? = null,
    val volumeChange: String? = null, // "UP", "DOWN", "MUTE", "MAX"
    val requiresConfirmation: Boolean = false
)

data class ChatMessage(
    val role: String, // "user" or "model"
    val text: String
)

class RkGeminiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val sessionHistory = mutableListOf<ChatMessage>()

    suspend fun processQuery(userInput: String): GeminiParsedResult = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d("RkGeminiService", "Gemini API key not configured, using local smart brain")
            return@withContext localSmartBrain(userInput)
        }

        try {
            val response = callGeminiApi(apiKey, userInput)
            if (response != null) {
                // Keep session context
                sessionHistory.add(ChatMessage("user", userInput))
                sessionHistory.add(ChatMessage("model", response.spokenResponse))
                if (sessionHistory.size > 20) {
                    sessionHistory.removeAt(0)
                    sessionHistory.removeAt(0)
                }
                return@withContext response
            }
        } catch (e: Exception) {
            Log.e("RkGeminiService", "Gemini API call failed, falling back to local brain", e)
        }

        localSmartBrain(userInput)
    }

    private fun callGeminiApi(apiKey: String, userInput: String): GeminiParsedResult? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val systemPrompt = """
You are RK, a personal AI assistant.
You are intelligent, calm, helpful and conversational.
Speak naturally like a helpful human assistant.
Do not sound robotic.
Keep answers concise unless the user asks for details.
Understand Bengali, Hindi and English naturally.
Never pretend that an action was completed if it was not actually completed by the Android system.
Ask confirmation before sensitive actions (making a phone call, sending an SMS).

When analyzing the user request, return STRICT JSON format only (no markdown, no backticks, no extra text):
{
  "spoken_response": "Short, natural, human-like voice response in the EXACT same language the user spoke (Bengali, Hindi, or English)",
  "detected_language": "bn" | "hi" | "en",
  "action_type": "OPEN_APP" | "SEARCH_GOOGLE" | "CALL" | "SMS" | "VOLUME_CONTROL" | "SYSTEM_SETTING" | "CONVERSATION",
  "action_target": "youtube" | "facebook" | "instagram" | "camera" | "gallery" | "settings" | "other" | null,
  "search_query": "string or null",
  "phone_number": "string or null",
  "sms_body": "string or null",
  "volume_change": "UP" | "DOWN" | "MUTE" | "MAX" | null,
  "requires_confirmation": boolean (set true for CALL and SMS)
}
Examples:
User: "RK, ইউটিউব খুলে দাও" -> {"spoken_response": "অবশ্যই, YouTube খুলছি।", "detected_language": "bn", "action_type": "OPEN_APP", "action_target": "youtube", "requires_confirmation": false}
User: "RK, camera kholo" -> {"spoken_response": "ঠিক আছে, Camera খুলছি।", "detected_language": "bn", "action_type": "OPEN_APP", "action_target": "camera", "requires_confirmation": false}
User: "RK, आज मौसम कैसा है?" -> {"spoken_response": "आज का मौसम देखने के लिए गूगल पर सर्च कर रहा हूँ।", "detected_language": "hi", "action_type": "SEARCH_GOOGLE", "search_query": "today weather", "requires_confirmation": false}
User: "RK, search latest android news" -> {"spoken_response": "Searching latest Android news for you.", "detected_language": "en", "action_type": "SEARCH_GOOGLE", "search_query": "latest android news", "requires_confirmation": false}
User: "RK, 9876543210 এ কল করো" -> {"spoken_response": "আপনি কি 9876543210 নম্বরে কল করতে চান? দয়া করে কনফার্ম করুন।", "detected_language": "bn", "action_type": "CALL", "phone_number": "9876543210", "requires_confirmation": true}
User: "RK, send sms to 9876543210 saying hello" -> {"spoken_response": "Would you like me to send an SMS to 9876543210? Please confirm.", "detected_language": "en", "action_type": "SMS", "phone_number": "9876543210", "sms_body": "hello", "requires_confirmation": true}
""".trimIndent()

        val contentsArray = JSONArray()

        // Include recent session history
        for (chat in sessionHistory.takeLast(6)) {
            val historyContent = JSONObject().apply {
                put("role", chat.role)
                put("parts", JSONArray().put(JSONObject().put("text", chat.text)))
            }
            contentsArray.put(historyContent)
        }

        // Current turn
        val currentContent = JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", userInput)))
        }
        contentsArray.put(currentContent)

        val jsonBody = JSONObject().apply {
            put("contents", contentsArray)
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("responseMimeType", "application/json")
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: return null

        if (!response.isSuccessful) {
            Log.e("RkGeminiService", "Gemini HTTP error ${response.code}: $responseBody")
            return null
        }

        val rootObj = JSONObject(responseBody)
        val candidates = rootObj.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null
        if (parts.length() == 0) return null

        val rawText = parts.getJSONObject(0).optString("text", "")
        if (rawText.isBlank()) return null

        return parseGeminiJson(rawText, userInput)
    }

    private fun parseGeminiJson(rawJson: String, originalInput: String): GeminiParsedResult {
        val clean = rawJson.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val obj = JSONObject(clean)
        val spoken = obj.optString("spoken_response", "Yes, I am here.")
        val lang = obj.optString("detected_language", detectLanguageLocally(originalInput))
        val actionTypeStr = obj.optString("action_type", "CONVERSATION")
        val target = if (obj.isNull("action_target")) null else obj.optString("action_target")
        val search = if (obj.isNull("search_query")) null else obj.optString("search_query")
        val phone = if (obj.isNull("phone_number")) null else obj.optString("phone_number")
        val sms = if (obj.isNull("sms_body")) null else obj.optString("sms_body")
        val vol = if (obj.isNull("volume_change")) null else obj.optString("volume_change")
        val reqConf = obj.optBoolean("requires_confirmation", actionTypeStr == "CALL" || actionTypeStr == "SMS")

        val actionType = when (actionTypeStr) {
            "OPEN_APP" -> ActionType.OPEN_APP
            "SEARCH_GOOGLE" -> ActionType.SEARCH_GOOGLE
            "CALL" -> ActionType.CALL
            "SMS" -> ActionType.SMS
            "VOLUME_CONTROL" -> ActionType.VOLUME_CONTROL
            "SYSTEM_SETTING" -> ActionType.SYSTEM_SETTING
            else -> ActionType.CONVERSATION
        }

        return GeminiParsedResult(
            spokenResponse = spoken,
            detectedLanguage = lang,
            actionType = actionType,
            actionTarget = target,
            searchQuery = search,
            phoneNumber = phone,
            smsBody = sms,
            volumeChange = vol,
            requiresConfirmation = reqConf
        )
    }

    // Highly responsive multi-lingual smart local brain for Bengali, Hindi & English
    fun localSmartBrain(userInput: String): GeminiParsedResult {
        val trimmed = userInput.trim()
        val lower = trimmed.lowercase()
        val lang = detectLanguageLocally(trimmed)

        // Clean out wake words like "rk", "hey rk", "आरके", "আরকে"
        val cleanPrompt = lower
            .replace("hey rk", "")
            .replace("rk", "")
            .replace("আরকে", "")
            .replace("आरके", "")
            .trim()

        // 1. YouTube
        if (cleanPrompt.contains("youtube") || cleanPrompt.contains("ইউটিউব") || cleanPrompt.contains("यूट्यूब")) {
            val response = when (lang) {
                "bn" -> "অবশ্যই, YouTube খুলছি।"
                "hi" -> "ज़रूर, YouTube खोल रहा हूँ।"
                else -> "Opening YouTube for you."
            }
            return GeminiParsedResult(response, lang, ActionType.OPEN_APP, actionTarget = "youtube")
        }

        // 2. Facebook
        if (cleanPrompt.contains("facebook") || cleanPrompt.contains("ফেসবুক") || cleanPrompt.contains("फेसबुक")) {
            val response = when (lang) {
                "bn" -> "Facebook খোলা হচ্ছে।"
                "hi" -> "Facebook खोल रहा हूँ।"
                else -> "Opening Facebook."
            }
            return GeminiParsedResult(response, lang, ActionType.OPEN_APP, actionTarget = "facebook")
        }

        // 3. Instagram
        if (cleanPrompt.contains("instagram") || cleanPrompt.contains("ইনস্টাগ্রাম") || cleanPrompt.contains("इंस्टाग्राम") || cleanPrompt.contains("insta")) {
            val response = when (lang) {
                "bn" -> "Instagram খোলা হচ্ছে।"
                "hi" -> "Instagram खोल रहा हूँ।"
                else -> "Opening Instagram."
            }
            return GeminiParsedResult(response, lang, ActionType.OPEN_APP, actionTarget = "instagram")
        }

        // 4. Camera
        if (cleanPrompt.contains("camera") || cleanPrompt.contains("ক্যামেরা") || cleanPrompt.contains("कैमरा") || cleanPrompt.contains("photo") || cleanPrompt.contains("ছবি")) {
            val response = when (lang) {
                "bn" -> "ঠিক আছে, Camera খুলছি।"
                "hi" -> "ठीक है, Camera खोल रहा हूँ।"
                else -> "Opening Camera."
            }
            return GeminiParsedResult(response, lang, ActionType.OPEN_APP, actionTarget = "camera")
        }

        // 5. Gallery
        if (cleanPrompt.contains("gallery") || cleanPrompt.contains("গ্যালারি") || cleanPrompt.contains("गैलरी") || cleanPrompt.contains("photos")) {
            val response = when (lang) {
                "bn" -> "গ্যালারি খোলা হচ্ছে।"
                "hi" -> "गैलरी खोल रहा हूँ।"
                else -> "Opening Gallery."
            }
            return GeminiParsedResult(response, lang, ActionType.OPEN_APP, actionTarget = "gallery")
        }

        // 6. Settings
        if (cleanPrompt.contains("settings") || cleanPrompt.contains("সেটিংস") || cleanPrompt.contains("सेटिंग")) {
            val response = when (lang) {
                "bn" -> "ডিভাইস সেটিংস খোলা হচ্ছে।"
                "hi" -> "डिवाइस सेटिंग्स खोल रहा हूँ।"
                else -> "Opening Device Settings."
            }
            return GeminiParsedResult(response, lang, ActionType.SYSTEM_SETTING, actionTarget = "settings")
        }

        // 7. Volume Control
        if (cleanPrompt.contains("volume") || cleanPrompt.contains("আওয়াজ") || cleanPrompt.contains("ভলিউম") || cleanPrompt.contains("आवाज़")) {
            val isUp = cleanPrompt.contains("up") || cleanPrompt.contains("বাড়া") || cleanPrompt.contains("बढ़ा") || cleanPrompt.contains("high") || cleanPrompt.contains("বেশি")
            val isDown = cleanPrompt.contains("down") || cleanPrompt.contains("কমা") || cleanPrompt.contains("कम") || cleanPrompt.contains("low")
            val isMute = cleanPrompt.contains("mute") || cleanPrompt.contains("নিঃশব্দ") || cleanPrompt.contains("म्यूट") || cleanPrompt.contains("silent")

            val direction = when {
                isMute -> "MUTE"
                isDown -> "DOWN"
                else -> "UP"
            }

            val response = when (lang) {
                "bn" -> if (isMute) "ভলিউম মিউট করা হয়েছে।" else if (isDown) "ভলিউম কমানো হচ্ছে।" else "ভলিউম বাড়ানো হচ্ছে।"
                "hi" -> if (isMute) "आवाज़ म्यूट कर दी गई है।" else if (isDown) "आवाज़ कम कर रहा हूँ।" else "आवाज़ बढ़ा रहा हूँ।"
                else -> if (isMute) "Volume muted." else if (isDown) "Lowering volume." else "Raising volume."
            }
            return GeminiParsedResult(response, lang, ActionType.VOLUME_CONTROL, volumeChange = direction)
        }

        // 8. Call (Sensitive action -> requires confirmation!)
        if (cleanPrompt.contains("call") || cleanPrompt.contains("কল") || cleanPrompt.contains("कॉल") || cleanPrompt.contains("dial") || cleanPrompt.contains("ডায়াল")) {
            val extractedNumber = extractPhoneNumber(trimmed)
            val response = when (lang) {
                "bn" -> if (extractedNumber != null) "আপনি কি $extractedNumber নম্বরে কল করতে চান? দয়া করে কনফার্ম করুন।" else "আপনি কি ফোন ডায়ালার খুলতে চান? কনফার্ম করুন।"
                "hi" -> if (extractedNumber != null) "क्या आप $extractedNumber पर कॉल करना चाहते हैं? कृपया पुष्टि करें।" else "क्या आप फोन डायलर खोलना चाहते हैं? पुष्टि करें।"
                else -> if (extractedNumber != null) "Would you like me to call $extractedNumber? Please confirm." else "Would you like to open the phone dialer? Please confirm."
            }
            return GeminiParsedResult(
                spokenResponse = response,
                detectedLanguage = lang,
                actionType = ActionType.CALL,
                phoneNumber = extractedNumber,
                requiresConfirmation = true
            )
        }

        // 9. SMS (Sensitive action -> requires confirmation!)
        if (cleanPrompt.contains("sms") || cleanPrompt.contains("মেসেজ") || cleanPrompt.contains("এসএমএস") || cleanPrompt.contains("मैसेज") || cleanPrompt.contains("message")) {
            val extractedNumber = extractPhoneNumber(trimmed)
            val response = when (lang) {
                "bn" -> if (extractedNumber != null) "আপনি কি $extractedNumber নম্বরে মেসেজ পাঠাতে চান? কনফার্ম করুন।" else "আপনি কি মেসেজ অ্যাপ খুলতে চান? কনফার্ম করুন।"
                "hi" -> if (extractedNumber != null) "क्या आप $extractedNumber को मैसेज भेजना चाहते हैं? पुष्टि करें।" else "क्या आप मैसेज ऐप खोलना चाहते हैं? पुष्टि करें।"
                else -> if (extractedNumber != null) "Would you like to compose an SMS to $extractedNumber? Please confirm." else "Would you like to open Messages? Please confirm."
            }
            return GeminiParsedResult(
                spokenResponse = response,
                detectedLanguage = lang,
                actionType = ActionType.SMS,
                phoneNumber = extractedNumber,
                requiresConfirmation = true
            )
        }

        // 10. Search Google
        if (cleanPrompt.contains("search") || cleanPrompt.contains("সার্চ") || cleanPrompt.contains("खोजो") || cleanPrompt.contains("ढूंढो") || cleanPrompt.contains("मौसम") || cleanPrompt.contains("weather") || cleanPrompt.contains("আবহাওয়া") || cleanPrompt.contains("খবর") || cleanPrompt.contains("news")) {
            val rawQuery = cleanPrompt
                .replace("search", "")
                .replace("সার্চ", "")
                .replace("for me", "")
                .replace("something", "")
                .replace("খোঁজো", "")
                .replace("खोजो", "")
                .replace("ढूंढो", "")
                .trim()
            val finalQuery = if (rawQuery.isNotBlank()) rawQuery else trimmed

            val response = when (lang) {
                "bn" -> "গুগল এ সার্চ করা হচ্ছে: $finalQuery"
                "hi" -> "गूगल पर सर्च कर रहा हूँ: $finalQuery"
                else -> "Searching Google for: $finalQuery"
            }
            return GeminiParsedResult(response, lang, ActionType.SEARCH_GOOGLE, searchQuery = finalQuery)
        }

        // 11. Conversational Greetings & Personality
        if (cleanPrompt.contains("কে তুমি") || cleanPrompt.contains("who are you") || cleanPrompt.contains("तुम कौन हो") || cleanPrompt.contains("তোমার নাম") || cleanPrompt.contains("तुम्हारा नाम")) {
            val response = when (lang) {
                "bn" -> "আমি আরকে (RK), আপনার ব্যক্তিগত এআই অ্যাসিস্ট্যান্ট। বলুন আমি কীভাবে আপনাকে সাহায্য করতে পারি?"
                "hi" -> "मैं आरके (RK) हूँ, आपका पर्सनल एआई असिस्टेंट। बताइए मैं आपकी कैसे सहायता कर सकता हूँ?"
                else -> "I am RK, your personal AI assistant. How may I assist you today?"
            }
            return GeminiParsedResult(response, lang, ActionType.CONVERSATION)
        }

        if (cleanPrompt.contains("হ্যালো") || cleanPrompt.contains("hello") || cleanPrompt.contains("hi") || cleanPrompt.contains("नमस्ते") || cleanPrompt.contains("নমস্কার")) {
            val response = when (lang) {
                "bn" -> "নমস্কার! আমি আরকে। বলুন, কী সাহায্য করতে পারি?"
                "hi" -> "नमस्ते! मैं आरके हूँ। बताइए, आज क्या करना है?"
                else -> "Hello! I am RK. How can I help you right now?"
            }
            return GeminiParsedResult(response, lang, ActionType.CONVERSATION)
        }

        // General Q&A / fallback
        val fallbackResponse = when (lang) {
            "bn" -> "আমি বুঝতে পেরেছি। আপনার জন্য এটি গুগলে অনুসন্ধান করে দেখছি।"
            "hi" -> "मैं समझ गया। आपके लिए यह गूगल पर सर्च कर रहा हूँ।"
            else -> "I understand. Let me search this for you on Google."
        }
        return GeminiParsedResult(fallbackResponse, lang, ActionType.SEARCH_GOOGLE, searchQuery = trimmed)
    }

    private fun detectLanguageLocally(text: String): String {
        for (ch in text) {
            val code = ch.code
            // Bengali Unicode block: 0x0980 - 0x09FF
            if (code in 0x0980..0x09FF) return "bn"
            // Devanagari (Hindi) Unicode block: 0x0900 - 0x097F
            if (code in 0x0900..0x097F) return "hi"
        }
        return "en"
    }

    private fun extractPhoneNumber(input: String): String? {
        val regex = Regex("\\b\\d{10}\\b|\\b\\+?\\d{10,13}\\b")
        return regex.find(input)?.value
    }

    fun clearSession() {
        sessionHistory.clear()
    }
}
