# Публікація в F-Droid

Опис, іконка і скріншоти лежать у `fastlane/metadata/android/` — F-Droid бере їх звідти.
У F-Droid йде збірка **Full**.

## F-Droid

Офіційна інструкція: https://f-droid.org/docs/Inclusion_How-To/

1. Зареєструйтесь на https://gitlab.com і зробіть fork https://gitlab.com/fdroid/fdroiddata
2. Створіть гілку `app.androcleaner` і додайте файл `docs/stores/app.androcleaner.yml` як `metadata/app.androcleaner.yml`.
3. (Бажано) перевірте локально: `fdroid lint app.androcleaner`, `fdroid rewritemeta app.androcleaner`, `fdroid build app.androcleaner`.
4. Відкрийте merge request у `fdroid/fdroiddata` з назвою `New app: Androcleaner`.
   В описі чесно вкажіть, що код написано з допомогою ШІ і перевірено вами: F-Droid має
   тимчасову політику щодо генеративного ШІ (https://gitlab.com/fdroid/admin/-/work_items/699) —
   він не заборонений, але очікується людська перевірка.

Рецензенти — волонтери, тож включення зазвичай займає від кількох тижнів до місяців.
Після merge апка з'являється в репозиторії за 24–48 годин.

Якщо F-Droid не зможе відтворити збірку байт-у-байт, вони попросять прибрати рядки
`Binaries` та `AllowedAPKSigningKeys` — тоді F-Droid підпише APK своїм ключем
(і оновлюватися між GitHub-версією та F-Droid-версією без перевстановлення не вийде).

## IzzyOnDroid — не підходить

Політика IzzyOnDroid відхиляє апки, код яких повністю або частково створено генеративним ШІ:
https://izzyondroid.org/docs/general/AppInclusionPolicy/
