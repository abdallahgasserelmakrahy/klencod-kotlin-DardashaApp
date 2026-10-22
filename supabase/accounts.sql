-- شغّله مرة واحدة: Supabase ← SQL Editor  (آمن لإعادة التشغيل)
-- لميزة الحسابات المتعددة: يسمح بتسجيل نفس جهاز الإشعارات (FCM token) لأكثر من حساب،
-- فتصل إشعارات كل الحسابات المحفوظة على الجهاز وليس الحساب النشط فقط.
-- بدون تشغيله: التطبيق يعمل، لكن الإشعارات تصل للحساب الذي سجّلت به الدخول آخر مرة فقط.

alter table public.push_tokens drop constraint if exists push_tokens_pkey;
alter table public.push_tokens add primary key (token, user_id);

create or replace function public.register_push_token(p_token text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'not authenticated'; end if;
  insert into public.push_tokens(token, user_id, updated_at)
  values (p_token, auth.uid(), now())
  on conflict (token, user_id) do update set updated_at = now();
end $$;

revoke all on function public.register_push_token(text) from public, anon;
grant execute on function public.register_push_token(text) to authenticated;
