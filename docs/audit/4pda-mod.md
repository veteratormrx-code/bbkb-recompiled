# Аудит APK 4pda

## Повторный прогон

Размер переданного APK — 154 054 189 байт. SHA-256 — `29e41f05fcdf1c9800732b19577b31c87fc4ed4adf74eb4c6653ffdbed3a8342`. Подпись проверена `apksigner`. Пакет `com.blackberry.keyboard`, версия `4.1904.3.16701-modern.4` (1384), minSdk и targetSdk 28, ABI arm64-v8a и armeabi-v7a. APK используется как локальный эталон.

Эталон установлен, включён и выбран как IME на Android 15 и Android 17. Оба прогона подтвердили `bb`, `bbb` и Backspace. В Android 17 использован тот же редактор harness, что для upstream и форка. [Smoke на Android 17](../testing/artifacts/audit-2026-11-24/api37/mod/results.json)

В архиве есть 3972 строки `emoji-18.tsv`, 3972 строки поискового каталога, Noto Color Emoji и фразовые данные RU/EN. Пять словарей RUL1 побайтно совпадают с ресурсами форка. [Словари](../dictionaries.md) [Каталог эмодзи](../emoji-support.md)

## Предыдущий прогон

Файл `BlackBerryKeyboard-mod-Android17.apk` установлен на тот же эмулятор (`adb install` — Success). Поведение мода рядом с нашей клавиатурой не снималось.

Статически: пакет — пропатченный `com.blackberry.keyboard`. В `assets/bbmodern` лежат RUL1-словари и TSV фраз. В DEX есть `RussianLexicon`, `PhrasePredictions`, `TextEditingTool` и клиенты ML Kit. Нативные библиотеки есть и для arm64, и для armeabi-v7a. Мы v7a не заявляем и не кладём.

Алгоритм мода по DEX не восстановлен: классы обфусцированы. Поведение фраз восстановлено по формату TSV (контекст из одного или двух слов и `слово:счётчик`), а не по декомпилированному коду.
