package com.business.gym_app.util

import android.content.Context
import android.util.Log
import com.business.gym_app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Перевод произвольного текста через Google Cloud Translation API (REST v2).
 *
 * Зачем: названия программ тренировок и упражнений хранятся на сервере/в Room одним
 * языком (русским), поэтому при смене языка приложения встроенного словаря
 * [WorkoutDictionary] не хватает — всё, чего нет в словаре, допереводится здесь.
 *
 * Ключ не хранится в коде: он лежит в файле .env в корне проекта (в .gitignore, шаблон —
 * .env.example), а Gradle подставляет его в BuildConfig на этапе сборки (app/build.gradle.kts).
 * В Google Cloud Console для этого ключа должен быть включён «Cloud Translation API»,
 * иначе сервер отвечает 403 API_KEY_SERVICE_BLOCKED. Если ключа нет — облачный перевод
 * выключен, названия переводятся только встроенным словарём [WorkoutDictionary].
 *
 * Переводы кэшируются в SharedPreferences, поэтому повторные запросы к API не выполняются.
 * Любая ошибка (нет ключа, нет сети, 403) не ломает UI — текст показывается как есть.
 */
object GoogleTranslate {
    private const val TAG = "GoogleTranslate"
    private const val ENDPOINT = "https://translation.googleapis.com/language/translate/v2"
    private const val PREFS = "translate_cache"

    /** Ограничение Cloud Translation API — 128 строк на запрос; берём с запасом. */
    private const val MAX_PER_REQUEST = 64

    /** Пауза после 403 (ключ заблокирован / API не включён), чтобы не долбить сервер. */
    private const val BLOCKED_COOLDOWN_MS = 15 * 60 * 1000L

    private val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** Один запрос за раз: параллельные карточки не должны дублировать одни и те же строки. */
    private val mutex = Mutex()

    /** Кэш в памяти: «target|text» -> перевод. */
    private val memory = HashMap<String, String>()

    /** Кэш из SharedPreferences: target -> (текст -> перевод). Заполняется лениво. */
    private val disk = HashMap<String, MutableMap<String, String>>()

    @Volatile
    private var blockedUntil = 0L

    @Volatile
    private var warnedMissingKey = false

    /**
     * Ключ Google Cloud Translation API из BuildConfig — собирается из .env
     * (см. .env.example и app/build.gradle.kts). Пустая строка — перевод выключен.
     */
    val apiKey: String = BuildConfig.GOOGLE_TRANSLATE_API_KEY

    /** true, если ключ задан и перевод через Google Cloud доступен. */
    fun isConfigured(): Boolean = apiKey.isNotBlank()

    private fun warnMissingKeyOnce() {
        if (warnedMissingKey) return
        warnedMissingKey = true
        Log.w(TAG, "GOOGLE_TRANSLATE_API_KEY is empty — задайте ключ в .env (шаблон: .env.example)")
    }

    /** Нормализует код языка: «ru-RU»/«RU» -> «ru». */
    fun normalize(target: String): String = target.lowercase().substringBefore('-')

    /**
     * Нужен ли тексту перевод на [target]:
     *  - для «en» — в тексте есть кириллица;
     *  - для «ru» — текст латинский и в нём нет кириллицы.
     * Так мы не переводим уже переведённый текст и не тратим запросы впустую.
     */
    fun needsTranslation(text: String, target: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val hasCyrillic = trimmed.any { it in '\u0400'..'\u04FF' }
        val hasLatin = trimmed.any { it in 'a'..'z' || it in 'A'..'Z' }
        return when (normalize(target)) {
            "en" -> hasCyrillic
            "ru" -> hasLatin && !hasCyrillic
            else -> false
        }
    }

    /** Перевод из кэша (память + SharedPreferences) или null, если перевода ещё нет. */
    fun cached(context: Context, text: String, target: String): String? {
        val key = text.trim()
        if (key.isEmpty()) return null
        val lang = normalize(target)
        synchronized(memory) {
            memory["$lang|$key"]?.let { return it }
            return diskMap(context.applicationContext, lang)[key]?.also { memory["$lang|$key"] = it }
        }
    }

    /**
     * Переводит [texts] на язык [target] и возвращает карту «исходный текст -> перевод».
     * Уже переведённые строки берутся из кэша, недостающие — пакетным запросом к Google.
     * При любой ошибке возвращается пустая карта (UI остаётся на исходном тексте).
     */
    suspend fun translate(context: Context, texts: Collection<String>, target: String): Map<String, String> {
        val lang = normalize(target)
        if (lang != "en" && lang != "ru") return emptyMap()
        val appContext = context.applicationContext
        if (apiKey.isBlank()) {
            warnMissingKeyOnce()
            return emptyMap()
        }
        if (System.currentTimeMillis() < blockedUntil) return emptyMap()

        val pending = synchronized(memory) {
            texts.map { it.trim() }
                .filter { it.isNotEmpty() && needsTranslation(it, lang) }
                .distinct()
                .filter { diskMap(appContext, lang)[it] == null }
        }
        if (pending.isEmpty()) return emptyMap()

        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val fresh = LinkedHashMap<String, String>()
                pending.chunked(MAX_PER_REQUEST).forEach { chunk ->
                    // Кэш мог заполниться, пока ждали блокировку
                    val missing = synchronized(memory) { chunk.filter { diskMap(appContext, lang)[it] == null } }
                    if (missing.isEmpty()) return@forEach
                    val translated = requestTranslations(missing, lang) ?: return@forEach
                    save(appContext, lang, translated)
                    fresh.putAll(translated)
                }
                fresh
            }
        }
    }

    private fun requestTranslations(texts: List<String>, target: String): Map<String, String>? {
        val body = JSONObject()
            .put("q", JSONArray(texts))
            .put("target", target)
            .put("format", "text")
            .toString()

        val request = Request.Builder()
            .url("$ENDPOINT?key=$apiKey")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    if (response.code == 401 || response.code == 403) {
                        // Ключ не подходит или Cloud Translation API не включён в проекте
                        blockedUntil = System.currentTimeMillis() + BLOCKED_COOLDOWN_MS
                    }
                    Log.w(TAG, "Translation failed: HTTP ${response.code} $payload")
                    null
                } else {
                    parse(payload, texts)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Translation request error: ${e.message}")
            null
        }
    }

    private fun parse(payload: String, source: List<String>): Map<String, String>? = try {
        val translations = JSONObject(payload).optJSONObject("data")?.optJSONArray("translations")
        if (translations == null) {
            null
        } else {
            val result = LinkedHashMap<String, String>()
            source.forEachIndexed { index, text ->
                val value = unescape(translations.optJSONObject(index)?.optString("translatedText").orEmpty())
                if (value.isNotBlank()) result[text] = value
            }
            result
        }
    } catch (e: Exception) {
        Log.w(TAG, "Bad translation response: ${e.message}")
        null
    }

    /** Google экранирует спецсимволы в translatedText — возвращаем читаемый текст. */
    private fun unescape(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#34;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
        .trim()

    private fun diskMap(context: Context, target: String): MutableMap<String, String> =
        disk.getOrPut(target) {
            val map = mutableMapOf<String, String>()
            val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(target, null)
            if (json != null) {
                try {
                    val obj = JSONObject(json)
                    obj.keys().forEach { key -> map[key] = obj.optString(key) }
                } catch (e: Exception) {
                    Log.w(TAG, "Broken translation cache for '$target': ${e.message}")
                }
            }
            map
        }

    private fun save(context: Context, target: String, entries: Map<String, String>) {
        if (entries.isEmpty()) return
        synchronized(memory) {
            val map = diskMap(context, target)
            entries.forEach { (text, translation) ->
                map[text] = translation
                memory["$target|$text"] = translation
            }
            val json = JSONObject()
            map.forEach { (text, translation) -> json.put(text, translation) }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(target, json.toString())
                .apply()
        }
    }
}
