# Аудит upstream BBKB

## Повторный прогон

На сервере upstream проверена вершина `main` — `ba8b9d0d1ab4aab36a167e771c803f04da2741e7`. Чистый detached worktree развёрнут в `Z:\BBK-upstream-audit`. Оригинальная настройка Gradle указывает JDK по пути macOS. Для сборки переданы Windows JDK 21 и путь к цепочке JDK 17 через аргументы командной строки. Очистка и debug-сборка прошли без правок upstream-кода. [Лог сборки](../testing/artifacts/audit-2026-11-24/upstream-gradle.txt)

В сравнении трёх клавиатур на одном AVD использован опубликованный upstream `5.0.0-beta.29` с application ID `dev.bbkb.ime`. Это позволяет держать на одном устройстве форк `dev.bbkb.ime.debug`. Хеш опубликованного APK совпал с `dist/manifest.json`. Устройство приняло `bb`, `bbb` и Backspace реальными нажатиями. [Smoke на Android 17](../testing/artifacts/audit-2026-11-24/api37/upstream/results.json)

## Предыдущий прогон

Чистое дерево: `c:\work\bbkey\bbkb-upstream`, `origin/main`, `ba8b9d0`. Текущие правки там не лежат.

Это `dev.bbkb.ime`, compileSdk 36, только arm64-v8a, движок XT9 через `com.blackberry.nuanceshim` и `xt9kdb-scaffold`. Уже есть буфер на 25 записей, эмодзи Emojibase, голос, inline autofill, темы Classic/Modern/Material/BB10.

На эмуляторе Android 17 это дерево не собиралось и не запускалось. Сравнение с нашей сборкой поэтому не сделано.
