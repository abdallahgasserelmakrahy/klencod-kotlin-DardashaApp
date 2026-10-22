// Supabase Edge Function: send-push
import { createClient } from "npm:@supabase/supabase-js@2";

const SB_URL = Deno.env.get("SUPABASE_URL")!;
const SB_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const SECRET = Deno.env.get("PUSH_WEBHOOK_SECRET") ?? "";
const SA = JSON.parse(Deno.env.get("FCM_SERVICE_ACCOUNT") ?? "{}");

const sb = createClient(SB_URL, SB_KEY, { auth: { persistSession: false } });

const b64url = (input: ArrayBuffer | string) => {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : new Uint8Array(input);
  let s = "";
  bytes.forEach((c) => (s += String.fromCharCode(c)));
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};

let cached: { token: string; exp: number } | null = null;

async function accessToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cached && cached.exp - 60 > now) return cached.token;

  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = b64url(JSON.stringify({
    iss: SA.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const pem = String(SA.private_key).replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claim}`));
  const jwt = `${header}.${claim}.${b64url(sig)}`;

  const r = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt }),
  });
  const j = await r.json();
  if (!j.access_token) throw new Error("oauth failed: " + JSON.stringify(j));
  cached = { token: j.access_token, exp: now + (j.expires_in ?? 3600) };
  return cached.token;
}

// deno-lint-ignore no-explicit-any
function preview(m: any): string {
  const body = String(m.body ?? "");
  if (m.view_once && m.image_path) return "① صورة لمرة واحدة";
  if (m.audio_path) return "🎤 رسالة صوتية";
  if (m.file_path) return "📎 " + (m.file_name ?? "ملف");
  if (body.includes("maps.google.com/?q=")) return "📍 موقع";
  if (m.image_path && !body.trim()) return "📷 صورة";
  return body.length > 120 ? body.slice(0, 120) + "…" : body;
}

const ok = (msg: string) => new Response(msg, { status: 200 });

Deno.serve(async (req) => {
  if (!SECRET || req.headers.get("x-webhook-secret") !== SECRET) {
    return new Response("unauthorized", { status: 401 });
  }
  try {
    const { record: m } = await req.json();
    if (!m || m.deleted) return ok("skip");
    if (m.expires_at && Date.parse(m.expires_at) < Date.now()) return ok("expired");

    const to: string = m.receiver;
    const from: string = m.sender;
    if (!to || !from || to === from) return ok("skip");

    const [blk, mute, toks, prof] = await Promise.all([
      sb.from("blocks").select("blocked").eq("blocker", to).eq("blocked", from).limit(1),
      sb.from("chat_prefs").select("muted").eq("user_id", to).eq("peer", from).eq("muted", true).limit(1),
      sb.from("push_tokens").select("token").eq("user_id", to),
      sb.from("profiles").select("username, display_name").eq("id", from).maybeSingle(),
    ]);
    if (blk.data?.length || mute.data?.length) return ok("blocked or muted");
    const tokens = (toks.data ?? []).map((t: { token: string }) => t.token);
    if (!tokens.length) return ok("no tokens");

    const title = prof.data?.display_name || prof.data?.username || "رسالة جديدة";
    const body = preview(m);
    const at = await accessToken();
    const url = `https://fcm.googleapis.com/v1/projects/${SA.project_id}/messages:send`;

    await Promise.all(tokens.map(async (token: string) => {
      const res = await fetch(url, {
        method: "POST",
        headers: { Authorization: `Bearer ${at}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          message: {
            token,
            data: { sender: from, receiver: to, title, body },
            android: { priority: "HIGH", ttl: "86400s" },
          },
        }),
      });
      if (!res.ok) {
        const e = await res.json().catch(() => ({}));
        const code = e?.error?.details?.find((d: { errorCode?: string }) => d.errorCode)?.errorCode;
        console.error("FCM error", res.status, JSON.stringify(e));
        if (code === "UNREGISTERED" || e?.error?.status === "NOT_FOUND") {
          await sb.from("push_tokens").delete().eq("token", token);
        }
      }
    }));
    return ok("sent");
  } catch (e) {
    console.error("send-push failed", e);
    return new Response("error", { status: 500 });
  }
});