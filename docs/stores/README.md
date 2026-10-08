# Публікація в F-Droid — покроково

Опис, іконка і скріншоти вже лежать у `fastlane/metadata/android/` — F-Droid бере їх звідти.
Метадані для F-Droid: [`app.androcleaner.yml`](app.androcleaner.yml) (перевірено `fdroid lint` — без зауважень).
У F-Droid іде збірка **Full**. Офіційна інструкція: https://f-droid.org/docs/Inclusion_How-To/

## 0. Перед подачею

- [ ] Реліз **v0.2.1** опубліковано на GitHub, і в ньому є файл `Androcleaner-Full-v0.2.1.apk`
      (F-Droid збирає з тегу `v0.2.1` і звіряє свою збірку з цим файлом).
- [ ] Ви переглянули код (F-Droid очікує, що автор відповідає за код, навіть написаний з допомогою ШІ).

## 1. Акаунт GitLab

1. Зареєструйтесь на https://gitlab.com/users/sign_up (можна увійти через GitHub).
2. Підтвердіть email. GitLab може попросити підтвердити акаунт телефоном або карткою —
   це антиспам-перевірка, гроші не списуються.

## 2. Fork репозиторію fdroiddata

1. Відкрийте https://gitlab.com/fdroid/fdroiddata
2. Натисніть **Fork** (угорі праворуч).
3. Namespace — ваш акаунт, назву не змінюйте → **Fork project**. Fork великий, створення займе кілька хвилин.

## 3. Додати файл метаданих (через браузер, без git)

1. У вашому fork відкрийте папку **metadata**.
2. Угорі натисніть **+** → **New file**.
3. Назва файлу: `app.androcleaner.yml`
4. Вміст: скопіюйте весь файл [`app.androcleaner.yml`](app.androcleaner.yml) **без рядків, що починаються з `#`**.
5. Внизу:
   - Commit message: `New app: Androcleaner`
   - Target branch: `app.androcleaner` (нова гілка) і поставте галочку **Start a new merge request with these changes**.
6. **Commit changes**.

## 4. Merge request

GitLab відкриє форму merge request. Перевірте:

- **Source**: `ваш-нік/fdroiddata` → гілка `app.androcleaner`
- **Target**: `fdroid/fdroiddata` → гілка `master`
- **Title**: `New app: Androcleaner`
- **Description**: виберіть шаблон **App inclusion** (якщо запропоновано), відмітьте пункти чекліста і додайте:

```
Androcleaner — honest storage cleaner: junk files, app cache, large files, trash.
No ads, no trackers, fully offline.

Source: https://github.com/FixerHack/Androcleaner
License: GPL-3.0-only

Build: `full` flavor. Releases are signed by me; the metadata includes Binaries +
AllowedAPKSigningKeys for reproducible builds. If the build is not reproducible,
I'm fine with F-Droid signing it instead.

Accessibility service: used only when the user starts a cache clean, to open each app's
info page in Settings and press Storage → Clear cache. It doesn't read other apps' content.

Disclosure: the code was written with the help of an AI assistant and reviewed by me.
```

Також поставте галочку **Allow commits from members who can merge to the target branch** —
так рецензенти зможуть самі виправити дрібниці.

Натисніть **Create merge request**.

## 5. Після подачі

1. Запуститься CI-перевірка (pipeline). Якщо вона червона — відкрийте лог і напишіть мені, що там.
2. Рецензенти залишать коментарі в MR. Відповідайте там же; зміни робіть, редагуючи файл у тій самій гілці.
3. Типові прохання рецензентів:
   - прибрати `Binaries` і `AllowedAPKSigningKeys`, якщо збірка не відтворюється байт-у-байт —
     тоді F-Droid підпише APK своїм ключем, і версії з F-Droid та GitHub не ставитимуться одна поверх одної;
   - додати `AntiFeatures` (наприклад, якщо вирішать, що Accessibility потребує позначки).
4. Час: рецензенти — волонтери, включення займає від кількох тижнів до місяців.
   Після merge апка з'являється в F-Droid за 24–48 годин.

## 6. Оновлення

Нічого подавати не треба: `UpdateCheckMode: Tags` — F-Droid сам знайде новий тег `vX.Y.Z`,
якщо в `app/build.gradle.kts` піднято `versionCode` і `versionName`, а на GitHub є реліз з `Androcleaner-Full-vX.Y.Z.apk`.

## IzzyOnDroid — не підходить

Їхня політика відхиляє апки, код яких повністю або частково створено генеративним ШІ:
https://izzyondroid.org/docs/general/AppInclusionPolicy/
