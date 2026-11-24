# ML Kit

Форк использует Translate 17.0.3 и Digital Ink 18.1.0. AVD Android 17 содержит Google Play и GMS. Тесты проводились в отдельном редакторе с искусственным текстом. [Translate](https://developers.google.com/ml-kit/language/translation?hl=ru) [Digital Ink](https://developers.google.com/ml-kit/vision/digital-ink-recognition/android?hl=ru)

| Сценарий | Результат |
| --- | --- |
| Загрузка пары EN→RU | PASS. Статус `Model ready: en → ru`. [Кадр](../testing/artifacts/audit-2026-11-24/api37/fork-translate-model-state/screen.png) |
| Редактор до исправления | FAIL. В активном редакторе панель показывала «Нет поля ввода». [Кадр](../testing/artifacts/audit-2026-11-24/api37/fork-translate-result/screen.png) |
| EN→RU на финальном APK | PASS. `cat → кошка`. Android сообщил `Active default network: none`. [Кадр](../testing/artifacts/audit-2026-11-24/api37/translate-cat-offline/screen.png) [Сеть](../testing/artifacts/audit-2026-11-24/api37/translate-cat-connectivity.txt) |
| Вставка перевода | PASS. В редакторе появилось «кошка». [Кадр](../testing/artifacts/audit-2026-11-24/api37/translate-cat-inserted/screen.png) |
| RU→EN в режиме полёта | PASS. `кошка → cat` после смены направления. [Кадр](../testing/artifacts/audit-2026-11-24/api37/translate-ru-en-offline/screen.png) |
| Переключение между всеми поддерживаемыми ML Kit языками | PARTIALLY IMPLEMENTED. В панели есть RU и EN. |
| Digital Ink, загрузка и офлайн-распознавание | NOT RUN сквозным тестом. |
| Ошибки моделей, отмена, удаление и сеть только Wi-Fi | NOT RUN по полной матрице. |
