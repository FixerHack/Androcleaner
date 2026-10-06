# Публікація в F-Droid та IzzyOnDroid

Опис, іконка і скріншоти лежать у `fastlane/metadata/android/` — обидва репозиторії беруть їх звідти.
У магазини йде збірка **Full**: встановлення з магазину Play Захист не блокує.

## IzzyOnDroid (швидко, зазвичай кілька днів)

1. Увійдіть на https://gitlab.com і відкрийте https://gitlab.com/IzzyOnDroid/repo/-/issues/new
2. Шаблон: **Request for Inclusion**. Текст:

```
App: Androcleaner
Source: https://github.com/FixerHack/Androcleaner
License: GPL-3.0-only
APK: GitHub Releases, asset Androcleaner-Full-v*.apk (please use the Full variant)
Description: honest storage/cache cleaner, no ads, no trackers, fully offline.
Accessibility service is used only to press "Clear cache" in Settings when the user starts a cache clean.
Signing cert SHA-256: c3d15f4402dc1787b1eaaebb93259b7c77c5dc4fd2378828a9616cc3e8fc471a
```

Після включення нові версії підтягуються автоматично протягом доби після релізу на GitHub.

## F-Droid (довго: місяць і більше на включення, 1–2 тижні на кожне оновлення)

1. Зробіть fork https://gitlab.com/fdroid/fdroiddata
2. Додайте файл `docs/stores/app.androcleaner.yml` як `metadata/app.androcleaner.yml`.
3. Створіть merge request з назвою `New app: Androcleaner`.

Якщо F-Droid не зможе відтворити збірку байт-у-байт, вони попросять прибрати рядки
`Binaries` та `AllowedAPKSigningKeys` — тоді F-Droid підпише APK своїм ключем
(і оновлюватися між GitHub-версією та F-Droid-версією без перевстановлення не вийде).
