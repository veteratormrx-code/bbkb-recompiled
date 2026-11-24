# Результаты аудита BBKB, 24.11.2026

Рабочая директория — `Z:\BBK`. Начальная ревизия форка — `8f76af7ed25a358ff633e19241875cc9998b747e`. Upstream — `ba8b9d0d1ab4aab36a167e771c803f04da2741e7`. Для сравнения на одном устройстве использован опубликованный upstream release beta.29. Чистая debug-сборка upstream также выполнена в отдельном worktree.

Windows 11 10.0.26200, Intel Core i5-12500H, 12 ядер / 16 потоков и около 32 ГиБ памяти. Gradle Wrapper 9.6.0, JDK 21, AGP 9.4.1, Kotlin 2.2.10, compileSdk/targetSdk 36, NDK 28.2.13676358 и CMake 3.22.1. AVD Android 17 API 37 с Google Play, GMS, 4096 МиБ, 1080×2400, 420 dpi и списком ABI `x86_64,arm64-v8a`. Для локального эксперимента также использован изолированный Android 15 AVD.

| Проверка | Результат | Доказательство |
| --- | --- | --- |
| Обе чистые debug-сборки и отдельная upstream-сборка | PASS | [clean](../testing/artifacts/audit-2026-11-24/clean.txt), [build](../testing/artifacts/audit-2026-11-24/build.txt), [upstream](../testing/artifacts/audit-2026-11-24/upstream-gradle.txt) |
| JVM, полный прогон | 3944 PASS, 20 FAIL, 25 SKIP из 3989 | [Сводка](../testing/artifacts/audit-2026-11-24/jvm-summary.json), [JUnit XML](../testing/artifacts/audit-2026-11-24/junit-full.zip) |
| Повторный JVM-прогон выбранных классов | 139 PASS, 6 FAIL из 145. Исправленные тесты путей и даты прошли. Проверки загрузки языкового манифеста продолжают падать в Robolectric Windows. | [Лог](../testing/artifacts/audit-2026-11-24/gradle-focused-retest.txt) |
| JVM-регрессии затронутых функций | 124 PASS перед последней правкой. После неё 114 PASS в выборке по буферу и полосе правки. Выборки пересекаются. | [JUnit 124](../testing/artifacts/audit-2026-11-24/junit-final-regression.zip), [JUnit 114](../testing/artifacts/audit-2026-11-24/junit-clipboard-final.zip) |
| Python публикации | 68 PASS из 68 | [Лог](../testing/artifacts/audit-2026-11-24/dist-python.txt) |
| Native C | 16 PASS, 1 FAIL, 2 этапа SKIP в общем прогоне. Проверка ELF отдельно PASS. | [Native](../testing/artifacts/audit-2026-11-24/native-final/native.txt), [ELF](../testing/artifacts/audit-2026-11-24/xt9-apk-guard.txt) |
| Native C после объединения | 21 этап PASS в WSL, включая отрицательные контроли. Для golden используется допуск `1e-4` и проверка состава кандидатов. | [Сводка](../testing/artifacts/audit-2026-11-24/merge-validation/native-summary.txt) |
| Основной ввод на Android 17 | 9 PASS из 9. По три сценария на каждую сборку. | [Upstream](../testing/artifacts/audit-2026-11-24/api37/upstream/results.json), [4PDA](../testing/artifacts/audit-2026-11-24/api37/mod/results.json), [форк](../testing/artifacts/audit-2026-11-24/api37/fork/results.json) |
| Цифровая WebView-форма | Форк открыл цифровую раскладку и ввёл `-12.5` | [Раскладка](../testing/artifacts/audit-2026-11-24/api37/fork-web-number-open/screen.png), [значение](../testing/artifacts/audit-2026-11-24/api37/fork-vkb-number/screen.png) |
| Открытие панелей форка | Видны правка текста и перевод. Подсказки на этих кадрах отсутствуют. | [Правка](../testing/artifacts/audit-2026-11-24/api37/fork-edit-board/screen.png), [перевод](../testing/artifacts/audit-2026-11-24/api37/fork-translate-board/screen.png) |
| Финальный APK | SHA-256 установленного файла совпал. Ввод `bb`, `bbb` и Backspace PASS на Android 17. | [Артефакт и подпись](../testing/artifacts/audit-2026-11-24/final-apk.json), [smoke](../testing/artifacts/audit-2026-11-24/api37/final-smoke/results.json) |
| ML Kit Translate | PASS для EN→RU, RU→EN и вставки результата без сети после загрузки модели. | [Отчёт](mlkit-report.md) |
| Замена debug-сборки | Сохранены четыре настройки при установке поверх переподписанного upstream с тем же application ID и versionCode. | [Миграция](upgrade-migration-report.md) |
| Инструментованный короткий ввод на API 35 | PASS для двух нажатий в тестовом редакторе | [Лог](../testing/artifacts/audit-2026-11-24/api35-instrumentation-retest.txt) |
| Инструментованный ввод на API 37 | 10 003 касания, ноль расхождений по длине порций, падений и ANR. PSS 266→296 МБ. Циклы панелей открыты. | [Нагрузка](stress-test-report.md) |
| Память эмулятора на хосте | Обрезка рабочего набора вернула 6,2 ГБ без перезапуска; сторож держит 3,5–3,8 ГБ и 226–277 потоков вместо 13 ГБ и 1 635 потоков. | [Ограничения](known-limitations.md) |
| Подписанный release | `app-release.apk`, RSA 4096, схема v2, отпечаток `9f:48:ab…40:63`; `zipalign -c 4` без нарушений, все `lib/*.so` проходят 16-КиБ проверку. | [Подпись](release-signing.md) |
| Изображения в буфере | PNG скопирован, показан в истории и вставлен в MIME-редактор на API 35 на итоговой сборке. | [Буфер обмена](clipboard-report.md) |
| Словари и фразы | 3 708 431 уникальная строка, 3 606 701 после нормализации. Индексы совпали после генерации из шардов. | [RUL1](../testing/artifacts/audit-2026-11-24/dictionaries.json), [RU/EN](../testing/artifacts/audit-2026-11-24/phrases.json) |

Из проверок напрямую затронутых функций прошло 71 JVM-тест по истории буфера, 4 по каталогу и поиску эмодзи, 3 по RUL1, 2 по контексту и 1 по палитре OLED. Повторный JVM-прогон проверял эти же классы, поэтому его результаты нельзя суммировать с полным прогоном как уникальные тесты.

[Покрытие пунктов задания](master-test-plan.md) [Сравнение функций](../feature-parity.md) [Память](../performance.md) [Автозамена](autocorrect-benchmark.md) [Настройки](settings-matrix.md) [Конфиденциальность](security-audit.md) [Реестр дефектов](bug-register.md)
