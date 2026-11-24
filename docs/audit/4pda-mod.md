# Аудит APK 4pda

Файл `BlackBerryKeyboard-mod-Android17.apk` установлен на тот же эмулятор (`adb install` — Success). Поведение мода рядом с нашей клавиатурой не снималось.

Статически: пакет — пропатченный `com.blackberry.keyboard`. В `assets/bbmodern` лежат RUL1-словари и TSV фраз. В DEX есть `RussianLexicon`, `PhrasePredictions`, `TextEditingTool` и клиенты ML Kit. Нативные библиотеки есть и для arm64, и для armeabi-v7a. Мы v7a не заявляем и не кладём.

Алгоритм мода по DEX не восстановлен: классы обфусцированы. Поведение фраз восстановлено по формату TSV (контекст из одного или двух слов и `слово:счётчик`), а не по декомпилированному коду.
