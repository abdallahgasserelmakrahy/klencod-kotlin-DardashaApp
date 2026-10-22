-- شغّله مرة واحدة: Supabase ← SQL Editor  (آمن لإعادة التشغيل)
-- يضيف حقول الملف الشخصي الجديدة + يضمن وجود أعمدة الرسائل والمؤقّت.

-- 1) الملف الشخصي: الاسم المعروض، نبذة، مدينة، تاريخ ميلاد، روابط
alter table public.profiles add column if not exists display_name text;
alter table public.profiles add column if not exists bio          text;
alter table public.profiles add column if not exists city         text;
alter table public.profiles add column if not exists birthday     date;
alter table public.profiles add column if not exists links        jsonb not null default '[]'::jsonb;

-- حدود الطول (تُضاف مرة واحدة فقط)
do $$ begin
  if not exists (select 1 from pg_constraint where conname = 'profiles_bio_len') then
    alter table public.profiles add constraint profiles_bio_len check (bio is null or char_length(bio) <= 300);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'profiles_display_name_len') then
    alter table public.profiles add constraint profiles_display_name_len check (display_name is null or char_length(display_name) <= 50);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'profiles_links_arr') then
    alter table public.profiles add constraint profiles_links_arr
      check (jsonb_typeof(links) = 'array' and jsonb_array_length(links) <= 5);
  end if;
end $$;

-- 2) أعمدة الرسائل (موجودة غالباً من نسخة الويب؛ هذا للتأكّد فقط)
alter table public.messages add column if not exists forwarded  boolean not null default false;
alter table public.messages add column if not exists view_once  boolean not null default false;
alter table public.messages add column if not exists viewed_at  timestamptz;
alter table public.messages add column if not exists expires_at timestamptz;

-- 3) مؤقّت الرسائل لكل محادثة (صف واحد لكل زوج a<b)
create table if not exists public.chat_timers (
  a    uuid not null,
  b    uuid not null,
  secs int  not null default 0,
  primary key (a, b)
);
alter table public.chat_timers enable row level security;
drop policy if exists chat_timers_participants_v2 on public.chat_timers;
create policy chat_timers_participants_v2 on public.chat_timers
  for all to authenticated
  using (auth.uid()::text = a::text or auth.uid()::text = b::text)
  with check (auth.uid()::text = a::text or auth.uid()::text = b::text);

-- لتصل تغييرات المؤقّت فورياً للطرف الآخر
do $$ begin
  alter publication supabase_realtime add table public.chat_timers;
exception when duplicate_object then null;
end $$;

-- ملاحظة: الملف الشخصي يُقرأ من الجميع ويُعدَّل من صاحبه بنفس سياسات profiles الحالية
-- (التي تعمل أصلاً لتعديل "about" و"avatar_path").
