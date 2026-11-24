"""Write deterministic RU/EN keyboard regression cases as UTF-8 CSV."""

import argparse
import csv
from pathlib import Path

WORDS = {
    "ru": """
        привет сегодня здравствуйте машина обычно спасибо пожалуйста завтра работа встреча
        телефон велосипед самолёт дорога автобус станция магазин покупка подарок деньги
        письмо сообщение звонок минута неделя правило пример ответ вопрос порядок
        погода дождик снегопад ветер солнце дерево яблоко огурец бананы картофель
        завтрак ужин печенье молоко мороженое тарелка бутылка кастрюля квартира комната
        подушка зеркало стулья диван учебник тетрадь карандаш библиотека урок учитель
        студент задача решение число формула рассказ история новости страницы музыка
        концерт картина игрушка собака кошка воробей лошадь медведь рыбалка палатка
        отпуск поездка деревня столица автобусу пешеход тротуар светофор книжка дневник
        упражнение здоровье больница аптека давление таблетки случай причина результат помощь
        компьютер клавиатура программа приложение документ загрузка настройки сохранение перевод словарь
    """.split(),
    "en": """
        hello today tomorrow yesterday because receive friend machine usually please
        thanks breakfast dinner cookies coffee kitchen bottle vegetable apple orange
        bicycle airplane station traffic journey holiday ticket airport suitcase hotel
        office meeting message calendar minute evening morning reminder project schedule
        school student teacher homework lesson notebook pencil library reading picture
        music concert guitar drawing painting theatre camera photograph birthday present
        doctor hospital medicine blanket pillow bedroom window garden weather sunshine
        rainbow thunder winter summer autumn spring mountain ocean river forest
        rabbit squirrel animal horses puppy kitten dolphin turtles elephant butterfly
        computer keyboard program software document settings download translate dictionary printer
        network password address account service package version numbers example response
    """.split(),
}

NEIGHBOR = {
    "ru": "йцукенгшщзхъфывапролджэячсмитьбюё",
    "en": "qwertyuiopasdfghjklzxcvbnm",
}

EXAMPLES = {
    "ru": "привте:привет рпивет:привет сегодян:сегодня здраствуйте:здравствуйте машына:машина абычно:обычно",
    "en": "teh:the recieve:receive freind:friend tommorow:tomorrow",
}

PROTECTED = {
    "ru": "Алексей Аксёненко Юлия Петрова Санкт-Петербург Йошкар-Ола онлайн офлайн подкаст краш дедлайн йота электрофорез синхрофазотрон",
    "en": "Aoife Nguyen São-Paulo OpenCorpora Kotlin Gradle GitHub JSON regex pytest debug podcast zettabyte websocket",
}


def make_rows():
    for lang, words in WORDS.items():
        examples = (pair.split(":") for pair in EXAMPLES[lang].split())
        for typed, expected in examples:
            yield lang, "curated typo", typed, expected
        for word in PROTECTED[lang].split():
            yield lang, "preserve", word, word
        for word in words:
            middle = len(word) // 2
            swap = next(index for index in range(len(word) - 1) if word[index] != word[index + 1])
            layout = NEIGHBOR[lang]
            replacement = layout[(layout.index(word[0]) + 1) % len(layout)] if word[0] in layout else layout[0]
            yield lang, "correct", word, word
            yield lang, "omission", word[:middle] + word[middle + 1:], word
            yield lang, "duplication", word[:middle] + word[middle] + word[middle:], word
            yield lang, "transposition", word[:swap] + word[swap + 1] + word[swap] + word[swap + 2:], word
            yield lang, "substitution", replacement + word[1:], word


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    rows = list(dict.fromkeys(make_rows()))
    if len(rows) < 1000:
        raise RuntimeError(f"Corpus unexpectedly small: {len(rows)}")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8", newline="") as out:
        writer = csv.writer(out, lineterminator="\n")
        writer.writerow(("language", "case", "typed", "target"))
        writer.writerows(rows)
    print(f"{len(rows)} cases")


if __name__ == "__main__":
    main()
