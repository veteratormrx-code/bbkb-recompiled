# Тестирование

## Воспроизведение текущего аудита

```powershell
.\tools\qa\Invoke-Audit.ps1 -Task Environment
.\tools\qa\Invoke-Audit.ps1 -Task CleanBuild
.\tools\qa\Invoke-Audit.ps1 -Task Jvm
.\tools\qa\Invoke-Audit.ps1 -Task Native
.\tools\qa\Invoke-Audit.ps1 -Task Dictionaries -Apk BlackBerryKeyboard-mod-Android17.apk
.\tools\qa\Invoke-Audit.ps1 -Task Dist
```

`Install` принимает список APK и серийный номер AVD. `Capture` собирает реальные PNG, XML и crash Logcat. Для `ime_smoke.py` нужно выбрать IME и измерить координаты видимых клавиш на конкретном AVD. Записи в редакторе создаются тапами. `KeyboardTapTest` из `:ime-harness` требует аргументов `bbkbIme`, `bbkbKeyX`, `bbkbKeyY`. Число нажатий задаёт `bbkbTapCount`. Без калибровки тест пропускается.

[Результаты](qa/test-results.md) [Покрытие задания](qa/master-test-plan.md)

## Предыдущий прогон

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
