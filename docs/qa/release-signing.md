# Подпись релиза

Релизные APK собираются с постоянным ключом, который лежит **вне репозитория**: Android требует,
чтобы обновление поверх установленного приложения было подписано тем же ключом, а сам ключ нельзя
класть в git.

## Где что лежит

| Что | Путь |
|---|---|
| Хранилище ключей | `%USERPROFILE%\.bbkb\release.keystore` |
| Параметры для Gradle | `%USERPROFILE%\.gradle\gradle.properties` (ключи `releaseStoreFile`, `releaseStorePassword`, `releaseKeyAlias`, `releaseKeyPassword`) |
| Сам ключ в репозитории | отсутствует и не должен появляться (`.gitignore`) |

Ключ: alias `bbkb`, RSA 4096, срок до 2056-11-16.
Отпечаток SHA-256:

```
9F:48:AB:89:1E:70:A0:88:72:A3:87:9B:9B:57:C4:A9:45:5F:AB:22:3C:9F:46:EB:1F:8E:DA:83:FD:D2:40:63
```

Отпечаток — это то, что должно совпадать у всех выпускаемых сборок. Проверка:

```powershell
keytool -list -v -keystore "$env:USERPROFILE\.bbkb\release.keystore" -alias bbkb
```

## Как собрать

```powershell
.\gradlew.bat :app:assembleRelease
# APK: app\build\outputs\apk\release\app-release.apk
```

Если `releaseStoreFile` не задан, `app/build.gradle` не подставляет `signingConfig` — релизный APK
собирается, но остаётся неподписанным, и установить его нельзя. Это сделано намеренно: сборка
никогда не откатывается на публичный debug-ключ.

Важно: `~/.gradle/gradle.properties` должен быть без BOM. С BOM (`EF BB BF` в начале файла) Gradle
разбирает первую строку как ключ `\uFEFFreleaseStoreFile`, `project.hasProperty('releaseStoreFile')`
возвращает `false`, и сборка молча выпускает `app-release-unsigned.apk`. Именно это и случилось
при первой попытке; BOM снят, и после этого появляется подписанный `app-release.apk`.

## Проверка собранного APK

```powershell
$bt = "$env:ANDROID_HOME\build-tools\36.0.0"
$apk = "app\build\outputs\apk\release\app-release.apk"
& "$bt\apksigner.bat" verify --verbose --print-certs $apk
& "$bt\zipalign.exe" -c -P 16 -v 4 $apk
```

Результат проверенной сборки до объединения интерфейсных коммитов:

| Свойство | Значение |
|---|---|
| Файл | `app/build/outputs/apk/release/app-release.apk` |
| Размер | 109 986 336 байт |
| SHA-256 | `9c560e30b0281fa04db3663c044b7c3b087105f3ed7bc8959f05daea68f06e91` |
| Схемы подписи | v2 (v1 выключен, v3 не включён) |
| Сертификат | `CN=BBKB, O=BBKB, C=RU`, RSA 4096, SHA-256 `9f48ab89…fdd24063` |
| 4-байтовое выравнивание | 0 нарушений |
| 16-КиБ выравнивание `lib/*.so` | все `arm64-v8a` библиотеки `OK` |
| ABI | `arm64-v8a` (единственный, `abiFilters` в `app/build.gradle`) |

`-P 16` проверяет выравнивание несжатых `.so` в APK. Для поддержки страниц 16 КиБ отдельно
проверяются сегменты `LOAD` внутри ELF и запуск на подходящем устройстве. В этом APK
`libdigitalink.so` имеет три `LOAD` с `p_align=0x1000`. У остальных шести библиотек значение
не ниже `0x4000`. Поддержка страниц 16 КиБ остаётся открытой для Digital Ink. [Результаты проверки](../testing/artifacts/audit-2026-11-24/merge-validation/verified-release.json) [Требования Android](https://developer.android.com/guide/practices/page-sizes)

## Почему ключ создан заново

До этой приёмки постоянного релизного ключа в репозитории и в профиле не было (единственный
`*.keystore` — `app/debug.keystore`, публичный debug-ключ). Поэтому ключ создан один раз и
зафиксирован здесь; **менять его нельзя** — иначе обновления перестанут устанавливаться поверх
предыдущих сборок.

Следствие: обновление поверх чужих сборок (`dev.bbkb.ime` от upstream beta.29, 4PDA-мод) этим
ключом невозможно — у них своя подпись. Миграция проверяется на паре наших сборок с одним и тем же
ключом. [Отчёт о миграции](upgrade-migration-report.md)
