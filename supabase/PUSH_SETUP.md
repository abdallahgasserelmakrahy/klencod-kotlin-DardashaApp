# إعداد الإشعارات الفورية (FCM)

الكود جاهز في التطبيق وفي `supabase/`. يلزمك فقط الإعداد التالي (مرة واحدة):

## 1) Firebase
1. console.firebase.google.com ← أنشئ مشروعاً (مجاني) ← أضف تطبيق **Android** باسم الحزمة:
   `com.klencod.dardasha`
2. نزّل **google-services.json** وضعه في مجلد `app/` (بجانب `app/build.gradle.kts`).
   - إن كنت تبني عبر GitHub Actions: ارفعه إلى المستودع في نفس المكان (الملف ليس سرّياً).
   - بدونه يُبنى التطبيق عادي لكن بلا إشعارات خلفية.
3. Project settings ← **Service accounts** ← *Generate new private key* ← نزّل ملف JSON.
   ⚠ هذا الملف **سرّي**: لا ترفعه إلى GitHub ولا تضعه في التطبيق.

## 2) Supabase: قاعدة البيانات
افتح `supabase/push.sql`، **غيّر `CHANGE_ME_SECRET`** إلى نص سرّي طويل، ثم شغّله في SQL Editor.

## 3) Supabase: الدالة (Edge Function)
باستخدام CLI:
```bash
supabase functions deploy send-push --no-verify-jwt --project-ref dmbzwuxnradjlonvyxso
supabase secrets set PUSH_WEBHOOK_SECRET="نفس النص السرّي" \
  FCM_SERVICE_ACCOUNT="$(cat service-account.json)" --project-ref dmbzwuxnradjlonvyxso
```
أو من اللوحة: Edge Functions ← Deploy a new function باسم `send-push` والصق محتوى
`supabase/functions/send-push/index.ts` وأوقف **Verify JWT**، ثم أضف السرّين من Edge Functions ← Secrets.

## 4) التجربة
ابنِ التطبيق وسجّل دخولاً على جهازين بحسابين مختلفين، **أغلق** التطبيق على أحدهما (اسحبه من المهام)
وأرسل له رسالة من الآخر.

إن لم يصل إشعار:
- Edge Functions ← send-push ← **Logs** (يُظهر أخطاء FCM أو الأسرار).
- تأكد أن جدول `push_tokens` فيه صف للمستخدم (يُسجَّل عند تسجيل الدخول).
- «إيقاف إجباري» (Force stop) من إعدادات الأندرويد يمنع FCM حتى تفتح التطبيق مرة أخرى.
- بعض الأجهزة (شاومي/هواوي/أوبو…) تقتل التطبيقات في الخلفية: فعّل «التشغيل التلقائي» وأوقف توفير البطارية للتطبيق.

## ما الذي يُرسل ولمن؟
- لكل رسالة جديدة إلى المستلم فقط، إلا إذا كانت المحادثة **مكتومة** عنده أو كان المرسِل **محظوراً**.
- نص الإشعار: النص، أو «📷 صورة» / «🎤 رسالة صوتية» / «📎 اسم الملف» / «📍 موقع» / «① صورة لمرة واحدة».
- والتطبيق مفتوح لا يظهر إشعار FCM (يتولّى الاتصال اللحظي ذلك).
- اسم المرسِل في الإشعار هو اسمه المعروض أو اسم المستخدم (وليس الاسم الذي حفظته له في جهات الاتصال).
