package com.klencod.dardasha.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * تخزين Backblaze B2 بجانب Supabase — اتصال مباشر من التطبيق (Native B2 API).
 * تنبيه: المفتاح داخل الـ APK؛ لا ترفع هذا الملف إلى مستودع عام.
 */
object B2 {
    /** false = الرجوع الكامل إلى Supabase Storage */
    const val ENABLED = true

    private const val KEY_ID = "005f72cc0ca20120000000001"
    private const val APP_KEY = "K005Gdu7cYf5FA7LGjb28eeYDtH/oJU"
    private const val BUCKET_NAME = "dardasha-files"

    /** الـ buckets التي تُحفظ في B2. avatars تبقى على Supabase (روابطها عامة). */
    private val BUCKETS = setOf("chat-images", "chat-audio", "chat-files")

    fun handles(bucket: String) = ENABLED && bucket in BUCKETS

    private class Auth(val token: String, val apiUrl: String, val downloadUrl: String, val bucketId: String)
    private class Resp(val code: Int, val body: ByteArray) {
        val text get() = String(body)
        val ok get() = code in 200..299
    }

    @Volatile private var auth: Auth? = null
    private val lock = Mutex()

    private fun http(method: String, url: String, headers: Map<String, String>, body: ByteArray? = null, read: Int = 120000): Resp {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 20000; c.readTimeout = read
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            return Resp(code, stream?.use { it.readBytes() } ?: ByteArray(0))
        } finally { c.disconnect() }
    }

    private suspend fun authorize(force: Boolean = false): Auth = lock.withLock {
        auth?.takeIf { !force }?.let { return it }
        withContext(Dispatchers.IO) {
            val basic = Base64.encodeToString("$KEY_ID:$APP_KEY".toByteArray(), Base64.NO_WRAP)
            val r = http("GET", "https://api.backblazeb2.com/b2api/v3/b2_authorize_account",
                mapOf("Authorization" to "Basic $basic"), read = 30000)
            if (!r.ok) error("B2 authorize ${r.code}: ${r.text}")
            val j = AppJson.parseToJsonElement(r.text).jsonObject
            val s = j["apiInfo"]!!.jsonObject["storageApi"]!!.jsonObject
            Auth(
                token = j["authorizationToken"]!!.jsonPrimitive.content,
                apiUrl = s["apiUrl"]!!.jsonPrimitive.content,
                downloadUrl = s["downloadUrl"]!!.jsonPrimitive.content,
                bucketId = s["bucketId"]!!.jsonPrimitive.content,
            ).also { auth = it }
        }
    }

    /** ينفّذ الطلب، وإن انتهت صلاحية التوكن (401) يسجّل الدخول من جديد ويعيد المحاولة مرة. */
    private suspend fun call(req: (Auth) -> Resp): Resp {
        var a = authorize()
        var r = withContext(Dispatchers.IO) { req(a) }
        if (r.code == 401) {
            a = authorize(force = true)
            r = withContext(Dispatchers.IO) { req(a) }
        }
        return r
    }

    /** v3 يقبل GET بالـ query، وبعض الحسابات/النسخ تتطلب POST بجسم JSON — نجرّب الاثنين. */
    private fun api(a: Auth, op: String, params: Map<String, String>, read: Int = 30000): Resp {
        val q = params.entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }
        val g = http("GET", "${a.apiUrl}/b2api/v3/$op?$q", mapOf("Authorization" to a.token), read = read)
        if (g.ok || g.code == 401) return g
        val body = buildJsonObject { params.forEach { (k, v) -> if (k == "maxFileCount") put(k, v.toInt()) else put(k, v) } }.toString()
        return http("POST", "${a.apiUrl}/b2api/v3/$op",
            mapOf("Authorization" to a.token, "Content-Type" to "application/json"), body.toByteArray(), read)
    }

    private fun enc(name: String) =
        name.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun key(bucket: String, path: String) = "$bucket/$path"

    private fun sha1(b: ByteArray) = MessageDigest.getInstance("SHA-1").digest(b).joinToString("") { "%02x".format(it) }

    suspend fun put(bucket: String, path: String, bytes: ByteArray, mime: String) {
        val name = key(bucket, path)
        val r = call { a ->
            val u = api(a, "b2_get_upload_url", mapOf("bucketId" to a.bucketId))
            if (!u.ok) return@call u
            val uj = AppJson.parseToJsonElement(u.text).jsonObject
            http("POST", uj["uploadUrl"]!!.jsonPrimitive.content, mapOf(
                "Authorization" to uj["authorizationToken"]!!.jsonPrimitive.content,
                "X-Bz-File-Name" to enc(name),
                "Content-Type" to mime,
                "X-Bz-Content-Sha1" to sha1(bytes),
            ), bytes)
        }
        if (!r.ok) error("B2 upload ${r.code}: ${r.text}")
    }

    suspend fun get(bucket: String, path: String): ByteArray {
        val name = key(bucket, path)
        val r = call { a ->
            http("GET", "${a.downloadUrl}/file/$BUCKET_NAME/${enc(name)}", mapOf("Authorization" to a.token))
        }
        if (!r.ok) error("B2 download ${r.code}")
        return r.body
    }

    suspend fun delete(bucket: String, path: String) {
        val name = key(bucket, path)
        val l = call { a ->
            api(a, "b2_list_file_names", mapOf("bucketId" to a.bucketId, "prefix" to name, "maxFileCount" to "1"))
        }
        if (!l.ok) return
        val f = AppJson.parseToJsonElement(l.text).jsonObject["files"]?.jsonArray?.firstOrNull()?.jsonObject ?: return
        if (f["fileName"]?.jsonPrimitive?.contentOrNull != name) return
        val id = f["fileId"]!!.jsonPrimitive.content
        call { a ->
            val body = buildJsonObject { put("fileName", name); put("fileId", id) }.toString()
            http("POST", "${a.apiUrl}/b2api/v3/b2_delete_file_version",
                mapOf("Authorization" to a.token, "Content-Type" to "application/json"), body.toByteArray(), 30000)
        }
    }
}
