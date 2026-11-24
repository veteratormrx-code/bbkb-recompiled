# Матрица паритета

Статусы не смешиваются: код сам по себе не означает EMULATOR PASS.

| Функция | Статус | Доказательство |
| --- | --- | --- |
| Сборка arm64 debug | BUILD PASS | `:app:assembleDebug` и `:ime-harness:assembleDebug`, 24.11.2026, APK 127 810 966 байт |
| Загрузка JNI/XT9 на Android 17 x86_64 | EMULATOR PASS | `pidof dev.bbkb.ime.debug` = 3931; logcat `XT9WORDS: inline='bb'`, `XT9KDB: on-screen KDBs for qwerty` |
| Экранная клавиатура | EMULATOR PASS | После `debug_force_vkb_mode=true`. Без этого AVD считается PKB и рисует только полосу. Кадр `docs/testing/artifacts/oled-vkb.png` |
| OLED с разделителями, основная сетка | EMULATOR PASS | Тот же кадр: чёрные клавиши, видимые вертикальные штрихи, подсказки `bbq` / `BBQing` / `BlackBerry` |
| OLED без разделителей | IMPLEMENTED | Палитра и `drawKeySeparators=false` в коде и `OledPaletteTest` (UNIT TEST PASS). Кадр `oled-seamless.png` показал Gboard, не нашу клавиатуру |
| Остальные панели OLED (эмодзи, буфер, перевод, рукопись, альбом) | NOT IMPLEMENTED как проверка | Скриншотов этих поверхностей нет |
| XT9 не вырезан | EMULATOR PASS | Подсказки на `bb` пришли из XT9 |
| Русские `.bbd` | BUILD PASS | Файлы в APK. Уникальных форм по всем пакетам: **3 708 431** (`tools/dictionaries/count_forms.py`) |
| Поиск при опечатке в первых 3 буквах | IMPLEMENTED | `BbdLexicon.lookupWithPrefixTypos`. На эмуляторе не измерялось |
| Биграммы и триграммы | IMPLEMENTED | Индекс: ru 197 067 контекстов, en 263 875. Ключ — 1 или 2 слова, счётчики суммируются. Набор на эмуляторе не сравнивался с 4pda |
| Автозамена лучше/не хуже XT9 | NOT IMPLEMENTED как измерение | Корпус 1000 фраз не прогонялся |
| Буфер 200 | UNIT TEST PASS | `ClipboardHistoryManagerTest` |
| Лимит 256 МиБ по факту файла и миниатюры | IMPLEMENTED | `ClipEntry.storedBytes(filesDir)` читает длину файла и `.thumb`. Переполнение на эмуляторе не гонялось |
| Вставка картинки commitContent | IMPLEMENTED | Код есть. На harness не вставлялось |
| Панель правки / перевод / рукопись в меню клавиатуры | IMPLEMENTED | Кнопки меню `-47/-48/-49`, `ToolBoardView`. Тап по глобусу открыл подсказку `BlackBerry`, не панель. EMULATOR PASS нет |
| ML Kit скачивание и офлайн-перевод | NOT IMPLEMENTED как проверка | GMS на образе есть (`pm path com.google.android.gms`). Модель не скачивалась |
| Вылет на `bb` | EMULATOR PASS как «не воспроизводится» | Набор `bb` не дал FATAL/SIGSEGV. Чинить нечего, пока нет стека |
| Числовые поля WebView | NOT IMPLEMENTED как проверка | Поле есть в harness, раскладка не снималась |
| Голос и автозаполнение | NOT IMPLEMENTED как проверка | Код upstream сохранён |
| Сравнение трёх клавиатур и p50/p95 | NOT IMPLEMENTED | Чистый worktree: `c:\work\bbkey\bbkb-upstream` (origin/main, ba8b9d0). Замеры не сняты |
| Подписанный APK | BUILD PASS | `dist/bbkb-mod-arm64-signed.apk`, ключ `c:\work\bbkey\signing\bbkb-release.jks` (вне git). SHA-256 сертификата `a4033228db801f2eaf9d90ff0b07bca44b5caba378f180c7342763dd27a5299a` |
