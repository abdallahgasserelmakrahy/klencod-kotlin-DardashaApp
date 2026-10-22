-- شغّله مرة واحدة: Supabase ← SQL Editor  (آمن لإعادة التشغيل)
-- ⚠ قبل التشغيل: استبدل CHANGE_ME_SECRET (في الدالة notify_new_message بالأسفل) بنص سرّي طويل،
--   ونفس النص ضعه في سر الدالة PUSH_WEBHOOK_SECRET (انظر PUSH_SETUP.md).

-- 1) توكنات أجهزة المستخدمين (يصل إليها السيرفر فقط، لا أحد عبر API)
create table if not exists public.push_tokens (
  token      text primary key,
  user_id    uuid not null references auth.users(id) on delete cascade,
  platform   text not null default 'android',
  updated_at timestamptz not null default now()
);
create index if not exists push_tokens_user_idx on public.push_tokens(user_id);
alter table public.push_tokens enable row level security;   -- بلا سياسات = مغلق أمام anon/authenticated

-- 2) تسجيل التوكن (ينقله لآخر مستخدم سجّل دخوله بالجهاز) وإلغاؤه عند الخروج
create or replace function public.register_push_token(p_token text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'not authenticated'; end if;
  insert into public.push_tokens(token, user_id, updated_at)
  values (p_token, auth.uid(), now())
  on conflict (token) do update set user_id = excluded.user_id, updated_at = now();
end $$;

create or replace function public.unregister_push_token(p_token text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then return; end if;
  delete from public.push_tokens where token = p_token and user_id = auth.uid();
end $$;

revoke all on function public.register_push_token(text)   from public, anon;
revoke all on function public.unregister_push_token(text) from public, anon;
grant execute on function public.register_push_token(text)   to authenticated;
grant execute on function public.unregister_push_token(text) to authenticated;

-- 3) عند كل رسالة جديدة نستدعي دالة Edge باسم send-push (عبر pg_net)
create extension if not exists pg_net with schema extensions;

create or replace function public.notify_new_message()
returns trigger language plpgsql security definer set search_path = public, extensions as $$
begin
  begin
    perform net.http_post(
      url     := 'https://dmbzwuxnradjlonvyxso.supabase.co/functions/v1/send-push',
      headers := jsonb_build_object(
                   'Content-Type', 'application/json',
                   'x-webhook-secret', 'CHANGE_ME_SECRET'),
      body    := jsonb_build_object('record', to_jsonb(new)),
      timeout_milliseconds := 5000
    );
  exception when others then
    null;  -- أي خطأ في الإشعار لا يجب أن يمنع حفظ الرسالة
  end;
  return new;
end $$;

drop trigger if exists trg_notify_new_message on public.messages;
create trigger trg_notify_new_message
  after insert on public.messages
  for each row execute function public.notify_new_message();
