# Производительность

## Сравнение на Android 17, API 37

Однократный замер `dumpsys meminfo` выполнен после одинаковой последовательности `bb` → `bbb` → Backspace в IME harness. Использованы upstream release, 4PDA и debug форка. Результат показывает размер в памяти в этой точке. Он не изолирует влияние разных вариантов сборки, очередности запуска и состояния кэшей.

| Показатель | Upstream release | 4PDA | Форк debug |
| --- | ---: | ---: | ---: |
| Размер APK, байт | 70 211 148 | 154 054 189 | 126 204 214 |
| TOTAL PSS, КиБ | 74 446 | 86 072 | 141 977 |
| TOTAL RSS, КиБ | 200 400 | 201 236 | 222 256 |
| Native Heap, PSS по строке детализации, КиБ | 25 099 | 31 812 | 26 385 |
| p50/p95/p99 задержек ввода и подсказок | NOT RUN | NOT RUN | NOT RUN |
| Холодный и повторный старт | NOT RUN | NOT RUN | NOT RUN |
| Объём моделей ML Kit и задержки распознавания | NOT RUN | NOT RUN | NOT RUN |

[Upstream meminfo](testing/artifacts/audit-2026-11-24/api37/upstream/meminfo.txt) [4PDA meminfo](testing/artifacts/audit-2026-11-24/api37/mod/meminfo.txt) [Форк meminfo](testing/artifacts/audit-2026-11-24/api37/fork/meminfo.txt)

## Предыдущий прогон

Единственный замер на устройстве, сразу после набора `bb` на Android 17:

`adb shell dumpsys meminfo dev.bbkb.ime.debug`

- TOTAL PSS: 159 530 КБ
- Native Heap: 38 603 КБ
- Dalvik Heap: 10 124 КБ

Размер debug APK: 127 810 966 байт. Подписанная копия: 127 979 073 байт.

p50/p95/p99 нажатий и подсказок, холодный и повторный старт, RAM upstream и 4pda, RAM после словарей и ML Kit не измерялись. Таблицу сравнения заполнять выдуманными числами нельзя.

Словари читаются в потоке движка подсказок, по одной корзине. Это свойство кода, не результат профилирования.
