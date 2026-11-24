# Тестирование

## Команды, которые прошли

```
gradlew :app:assembleDebug :ime-harness:assembleDebug
gradlew :app:testDebugUnitTest --tests dev.bbkb.ime.modernlexicon.BbdLexiconTest --tests dev.bbkb.ime.keyboard.OledPaletteTest --tests dev.bbkb.ime.keyboard.inputboard.clipboard.ClipboardHistoryManagerTest
```

Полный набор JVM-тестов и native-тесты `xt9kdb-scaffold` в этом прогоне не запускались.

## Эмулятор

Образ: `system-images;android-37.0;google_apis_playstore;x86_64`, окно видно на рабочем столе.

Установка трёх APK прошла. Набор `bb` не уронил процесс. Кадры:

- `docs/testing/artifacts/oled-vkb.png` — наша OLED-сетка с разделителями.
- `docs/testing/artifacts/translate-board.png` — тап не открыл панель перевода.
- `docs/testing/artifacts/oled-seamless.png` — на экране Gboard, бесшовная тема не доказана.

Чистый upstream для сравнения: `c:\work\bbkey\bbkb-upstream`, коммит `ba8b9d0`. Он не собирался и не ставился в этом прогоне.
