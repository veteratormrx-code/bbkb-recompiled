# Визуальное сравнение на Android 17

Upstream, 4PDA и форк сняты на одном AVD 1080×2400, 420 dpi. [Upstream](../testing/artifacts/audit-2026-11-24/api37/upstream/type-bb.png) [4PDA](../testing/artifacts/audit-2026-11-24/api37/mod/type-bb.png) [Форк](../testing/artifacts/audit-2026-11-24/api37/fork/type-bb.png)

Финальная debug-сборка показала все шесть стилей в английской буквенной раскладке. После смены настройки запускался новый процесс IME. Для визуальных сценариев использовался debug-режим экранной клавиатуры. [Снимки шести тем](../testing/artifacts/audit-2026-11-24/api37/themes/) [Матрица кадров](../testing/artifacts/audit-2026-11-24/api37/themes/results.json)

| Контрольная точка | OLED с разделителями | OLED без разделителей |
| --- | --- | --- |
| Фон, x10 y1750 | #000000 | #000000 |
| Фон, x10 y1900 | #000000 | #000000 |
| Граница клавиш, x108 y1799 | #3A3A3A | #000000 |
| Светлый символ, x300 y1890 | #E8E8E8 | #E8E8E8 |

Для бесшовной темы сняты состояния с цифровым рядом и без него. [Ряд включён](../testing/artifacts/audit-2026-11-24/api37/themes/oled_seamless-row-on.png) [Ряд выключен](../testing/artifacts/audit-2026-11-24/api37/themes/oled_seamless-row-off.png) [Замеры RGB](../testing/artifacts/audit-2026-11-24/api37/themes/oled-pixels.csv)

На финальном APK через панель правки выделено и вырезано слово «кошка», после вставки оно появилось в исходном редакторе. Кнопка «Предыдущее слово» сохранила текст. На кадрах правки и перевода обычные подсказки поверх панели отсутствуют. [После вырезания](../testing/artifacts/audit-2026-11-24/api37/final-edit-cut/screen.png) [После вставки](../testing/artifacts/audit-2026-11-24/api37/final-edit-paste/screen.png) [Предыдущее слово](../testing/artifacts/audit-2026-11-24/api37/final-edit-word-back/screen.png)

Сравнение hitboxes, портретной и альбомной ориентации, вариантов ширины, TalkBack, русской раскладки и каждой дополнительной панели в каждой теме остаётся открытым.
