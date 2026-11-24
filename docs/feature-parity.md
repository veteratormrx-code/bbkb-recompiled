# Матрица паритета

Что именно изменилось относительно оригинального BBKB и APK мода: [Форк и оригинал](fork-vs-upstream.md).

## Текущий сравнительный прогон

Наличие функции указано по исходникам или ресурсам. PASS относится к конкретному тесту, указанному в последнем столбце.

| Функция | Upstream | 4PDA | Форк | Подтверждение |
| --- | --- | --- | --- | --- |
| Базовый английский ввод | IMPLEMENTED | IMPLEMENTED | IMPLEMENTED | На Android 17 у всех PASS для `bb`, `bbb` и Backspace. |
| Нативный движок arm64 | IMPLEMENTED | IMPLEMENTED | IMPLEMENTED | Все три установлены и принимают ввод на x86_64 AVD с ARM-трансляцией. Для форка есть журналы XT9. |
| Русские словари RUL1 | NOT IMPLEMENTED | IMPLEMENTED | IMPLEMENTED | PASS структуры и побайтного сравнения модификаций. Качество автозамены по корпусу NOT RUN. |
| Фразовые индексы RU/EN | PARTIALLY IMPLEMENTED через XT9 | IMPLEMENTED | IMPLEMENTED | Генерация индексов форка из эталонных TSV воспроизводится. JUnit backoff PASS, сквозное качество NOT RUN. |
| Цифровой ряд и OLED | Отдельные стили upstream | По ресурсам не установлено | IMPLEMENTED | JVM палитры PASS. Сняты обе OLED-темы и включённый/выключенный ряд. |
| HTML `input type=number` | NOT RUN | NOT RUN | IMPLEMENTED | На Android 17 форк ввёл `-12.5` на экранной цифровой раскладке. |
| История буфера | IMPLEMENTED | IMPLEMENTED | IMPLEMENTED | Форк PASS по 71 JVM-сценарию. Сквозной `commitContent` и реальный полный предел диска требуют проверки. |
| Эмодзи | Emojibase 16 | Каталог Emoji 18 и Noto | Emojibase с дополнением 17/18 | PASS четырёх JVM-сценариев форка. Отрисовка и редактор проверяются отдельно. |
| Панель редактирования | PARTIALLY IMPLEMENTED, FCC | IMPLEMENTED по ресурсу и коду | IMPLEMENTED | Форк PASS для выделения всего, вырезания, вставки и сохранения текста при переходе к предыдущему слову. |
| Перевод и рукопись ML Kit | NOT IMPLEMENTED | IMPLEMENTED по ресурсам | PARTIALLY IMPLEMENTED | Форк PASS для офлайн RU/EN и вставки результата. Выбор языка ограничен RU/EN. Полная проверка моделей и рукописи NOT RUN. |
| Голос и inline-автозаполнение | IMPLEMENTED | IMPLEMENTED по ресурсам | IMPLEMENTED по коду | Сквозная проверка NOT RUN. |
| Переходы панелей без наложений | NOT RUN | NOT RUN | PARTIALLY IMPLEMENTED | Матрица быстрых переходов и запоздавших результатов требует проверки. |
| Миграция пользовательских данных | NOT RUN | NOT RUN | PARTIALLY VERIFIED | Четыре настройки сохранились при замене совместимо подписанного debug APK на Android 15 с тем же versionCode. |

[Результаты](qa/test-results.md) [Покрытие задания](qa/master-test-plan.md)

## Предыдущий прогон

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
