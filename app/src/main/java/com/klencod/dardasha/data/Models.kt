package com.klencod.dardasha.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive

/** الـ id قد يكون رقماً (bigint) أو نصاً (uuid) — نقرأه دائماً كنص. */
object FlexId : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("FlexId", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String {
        val d = decoder as? JsonDecoder ?: return decoder.decodeString()
        return d.decodeJsonElement().jsonPrimitive.content
    }
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

@Serializable
data class Message(
    @Serializable(with = FlexId::class) val id: String,
    val sender: String,
    val receiver: String,
    val body: String? = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("viewed_at") val viewedAt: String? = null,
    val deleted: Boolean? = false,
    val edited: Boolean? = false,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("audio_path") val audioPath: String? = null,
    @SerialName("audio_secs") val audioSecs: Double? = null,
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("file_type") val fileType: String? = null,
    @SerialName("reply_to") @Serializable(with = FlexId::class) val replyTo: String? = null,
    @SerialName("view_once") val viewOnce: Boolean? = false,
    @SerialName("expires_at") val expiresAt: String? = null,
    val forwarded: Boolean? = false,
    /** محلي فقط: حذفها المرسِل لكن أبقيناها بفضل «منع الحذف». */
    val delSaved: Boolean? = false,
) {
    val isDeleted get() = deleted == true
    val isEdited get() = edited == true
    val text get() = body ?: ""
}

@Serializable
data class Profile(
    val id: String,
    val username: String = "",
    @SerialName("avatar_path") val avatarPath: String? = null,
    val about: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val bio: String? = null,
    val city: String? = null,
    val birthday: String? = null,
    val links: List<ProfileLink>? = null,
)

/** رابط في الملف الشخصي (انستغرام، تيليغرام، موقع...). */
@Serializable
data class ProfileLink(val title: String = "", val url: String = "")

/** يجعل الرابط صالحاً (http/https فقط) أو يرجع null. */
fun normalizeUrl(raw: String): String? {
    var u = raw.trim()
    if (u.isEmpty() || u.any { it.isWhitespace() }) return null
    if (!Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(u)) u = "https://$u"
    val m = Regex("^(https?)://([^/?#]+)", RegexOption.IGNORE_CASE).find(u) ?: return null
    val host = m.groupValues[2].substringAfter('@').substringBefore(':')
    if (!host.contains('.') || host.startsWith('.') || host.endsWith('.')) return null
    return u
}

/** اسم مختصر للموقع من الرابط، مع رمز مناسب للمنصات المعروفة. */
fun linkLabel(url: String): String {
    val host = Regex("^https?://([^/?#]+)", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)
        ?.substringAfter('@')?.substringBefore(':')?.lowercase()?.removePrefix("www.") ?: return url
    return when {
        host.endsWith("instagram.com") -> "📸 Instagram"
        host == "x.com" || host.endsWith("twitter.com") -> "🐦 X"
        host.endsWith("facebook.com") || host == "fb.com" -> "📘 Facebook"
        host.endsWith("tiktok.com") -> "🎵 TikTok"
        host.endsWith("snapchat.com") -> "👻 Snapchat"
        host.endsWith("youtube.com") || host == "youtu.be" -> "▶️ YouTube"
        host == "t.me" || host.endsWith("telegram.me") -> "✈️ Telegram"
        host == "wa.me" || host.endsWith("whatsapp.com") -> "💬 WhatsApp"
        host.endsWith("github.com") -> "💻 GitHub"
        host.endsWith("linkedin.com") -> "💼 LinkedIn"
        else -> "🔗 $host"
    }
}

@Serializable
data class Reaction(
    @SerialName("message_id") @Serializable(with = FlexId::class) val messageId: String,
    @SerialName("user_id") val userId: String,
    val emoji: String,
)

@Serializable
data class Contact(
    val contact: String,
    val nick: String? = null,
    val fav: Boolean? = false,
)

@Serializable
data class BlockRow(val blocked: String)

@Serializable
data class ChatTimer(val a: String, val b: String, val secs: Int? = 0)

/** تفضيلات المحادثة الخاصة بالمستخدم: تثبيت / أرشفة / كتم */
@Serializable
data class ChatPref(
    val peer: String,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val muted: Boolean = false,
)

/** رسالة مميّزة بنجمة (خاصة بالمستخدم) */
@Serializable
data class StarRow(
    @SerialName("message_id") @Serializable(with = FlexId::class) val messageId: String,
)

/** مستند تم اختياره من الجهاز قبل الرفع */
class PickedDoc(val name: String, val mime: String, val bytes: ByteArray)

/** موقع مرسل داخل نص الرسالة (رابط خرائط) */
data class GeoPoint(val lat: Double, val lng: Double)

private val GEO_RE = Regex("""https://maps\.google\.com/\?q=(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")

fun geoUrl(lat: Double, lng: Double) = "https://maps.google.com/?q=%.6f,%.6f".format(java.util.Locale.US, lat, lng)

fun parseGeo(text: String?): GeoPoint? {
    val m = GEO_RE.find(text ?: return null) ?: return null
    val lat = m.groupValues[1].toDoubleOrNull() ?: return null
    val lng = m.groupValues[2].toDoubleOrNull() ?: return null
    return GeoPoint(lat, lng)
}
