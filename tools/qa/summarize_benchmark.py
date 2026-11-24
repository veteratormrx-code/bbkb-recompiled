#!/usr/bin/env python3
"""Turn the raw autocorrect benchmark reports into the comparison tables.

Reads docs/testing/artifacts/audit-2026-11-24/bench/<build>-<lang>.json (written by
tools/qa/Invoke-ImeBenchmark.ps1) and writes:

  docs/qa/autocorrect-benchmark.md   the tables a reader wants
  docs/qa/autocorrect-benchmark.json machine-readable metrics

Definitions, kept strict on purpose:

  committed     what the field holds after the space that ends the word
  autocorrect   committed == target and the typed word was wrong (a real repair)
  false positive committed != target and != typed (the keyboard broke a word)
  preserved     committed == typed (no substitution happened)
  offered recall the target appears somewhere in the words the keyboard displayed
                 above the letter keys; it is a display subset, not the engine's full list,
                 so it can only under-report quality

Nothing is derived from unit tests or the engine's internals: every number comes from touch
events on the installed keyboard and the text that ended up in the field.
"""
from __future__ import annotations

import json
import statistics
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BENCH = ROOT / "docs/testing/artifacts/audit-2026-11-24/bench"
OUT_MD = ROOT / "docs/qa/autocorrect-benchmark.md"
OUT_JSON = ROOT / "docs/qa/autocorrect-benchmark.json"

BUILDS = ["fork", "upstream", "mod"]
LABELS = {
    "fork": "Форк (dev.bbkb.ime.debug)",
    "upstream": "Upstream beta.29 (dev.bbkb.ime)",
    "mod": "4PDA-мод 4.1904.3.16701",
}
LANGUAGES = ["en", "ru"]
# Cases where the typed word is already the desired word: any substitution is a false positive.
PRESERVE_CASES = {"preserve"}
# Cases where the typed word is wrong: the keyboard should repair it.
REPAIR_CASES = {"curated typo", "omission", "duplication", "transposition", "substitution"}


def percentile(values: list[float], share: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, int(round(share * (len(ordered) - 1)))))
    return ordered[index]


def load_report(build: str, language: str) -> dict:
    path = BENCH / f"{build}-{language}.json"
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def load_rows(build: str, language: str) -> list[dict]:
    return load_report(build, language).get("results", [])


def same(row: dict, value: str | None, expected: str | None) -> bool:
    """Compares against the expectation, folding case when the run could not shift.

    A build whose shift key does not take synthetic touch is typed in lower case, so its results
    carry caseFolded; without folding, every sentence-case row would score as a miss.
    """
    value = (value or "").strip()
    expected = (expected or "").strip()
    if row.get("caseFolded"):
        return value.lower() == expected.lower()
    return value == expected


def metric_block(rows: list[dict], report: dict | None = None) -> dict:
    ran = [row for row in rows if row.get("status") == "OK"]
    repair = [row for row in ran if row.get("case") in REPAIR_CASES]
    preserve = [row for row in ran if row.get("case") in PRESERVE_CASES]
    correct = [row for row in ran if row.get("case") == "correct"]

    def repaired(row: dict) -> bool:
        return same(row, row.get("afterSpace"), row.get("target")) and row.get("typed") != row.get("target")

    def broken(row: dict) -> bool:
        committed = (row.get("afterSpace") or "").strip()
        if committed in ("", row.get("typed"), row.get("target")):
            return False
        if row.get("caseFolded") and committed.lower() in (
            (row.get("typed") or "").lower(), (row.get("target") or "").lower()
        ):
            return False
        return True

    tap_latency = [row["tapToCharacterMs"] for row in ran if (row.get("tapToCharacterMs") or -1) >= 0]
    suggestion_latency = [
        row["suggestionLatencyMs"]
        for row in ran
        if (row.get("suggestionLatencyMs") or -1) >= 0 and row.get("suggestionsChanged")
    ]
    return {
        "rows_total": len(rows),
        "rows_run": len(ran),
        "rows_skipped": len(rows) - len(ran),
        "repair_rows": len(repair),
        "repaired": sum(1 for row in repair if repaired(row)),
        "repair_rate": (sum(1 for row in repair if repaired(row)) / len(repair)) if repair else 0.0,
        "false_positives_on_typos": sum(1 for row in repair if broken(row)),
        "preserve_rows": len(preserve),
        "preserved_words": sum(1 for row in preserve if same(row, row.get("afterSpace"), row.get("typed"))),
        "preserve_rate": (
            sum(1 for row in preserve if same(row, row.get("afterSpace"), row.get("typed"))) / len(preserve)
        ) if preserve else 0.0,
        "correct_rows": len(correct),
        "correct_left_alone": sum(1 for row in correct if same(row, row.get("afterSpace"), row.get("typed"))),
        "case_folded_rows": sum(1 for row in ran if row.get("caseFolded")),
        "shift_available": bool((report or {}).get("shiftAvailable", True)),
        "offered_recall": (sum(1 for row in ran if row.get("offeredHit")) / len(ran)) if ran else 0.0,
        "tap_p50_ms": percentile(tap_latency, 0.50),
        "tap_p95_ms": percentile(tap_latency, 0.95),
        "tap_p99_ms": percentile(tap_latency, 0.99),
        "suggestion_p50_ms": percentile(suggestion_latency, 0.50),
        "suggestion_p95_ms": percentile(suggestion_latency, 0.95),
        "suggestion_p99_ms": percentile(suggestion_latency, 0.99),
        "suggestion_samples": len(suggestion_latency),
        "suggestion_mean_ms": statistics.fmean(suggestion_latency) if suggestion_latency else 0.0,
    }


def percent(value: float) -> str:
    return f"{value * 100:.1f}%".replace(".", ",")


def build_table(metrics: dict, language: str) -> list[str]:
    header = (
        "| Сборка | Строк | Опечаток | Исправлено | Ложно исправлено | Сохранено верных | "
        "Recall в подсказках | Тап p50/p95/p99, мс | Подсказки p50/p95/p99, мс |"
    )
    lines = [header, "|" + "---|" * 9]
    for build in BUILDS:
        block = metrics.get(build, {}).get(language)
        if not block:
            lines.append(f"| {LABELS[build]} | — | — | — | — | — | — | — | — |")
            continue
        lines.append(
            "| {label} | {rows} | {repair} | {repaired} ({rate}) | {fp} | {pres} ({prate}) | {recall} | "
            "{tp50}/{tp95}/{tp99} | {sp50}/{sp95}/{sp99} |".format(
                label=LABELS[build],
                rows=block["rows_run"],
                repair=block["repair_rows"],
                repaired=block["repaired"],
                rate=percent(block["repair_rate"]),
                fp=block["false_positives_on_typos"],
                pres=block["preserved_words"],
                prate=percent(block["preserve_rate"]),
                recall=percent(block["offered_recall"]),
                tp50=f"{block['tap_p50_ms']:.0f}",
                tp95=f"{block['tap_p95_ms']:.0f}",
                tp99=f"{block['tap_p99_ms']:.0f}",
                sp50=f"{block['suggestion_p50_ms']:.0f}",
                sp95=f"{block['suggestion_p95_ms']:.0f}",
                sp99=f"{block['suggestion_p99_ms']:.0f}",
            )
        )
    return lines


def by_case(rows: list[dict]) -> dict[str, dict]:
    groups: dict[str, dict] = {}
    for row in rows:
        if row.get("status") != "OK":
            continue
        case = row.get("case", "?")
        group = groups.setdefault(case, {"rows": 0, "autocorrected": 0, "preserved": 0, "offered": 0})
        group["rows"] += 1
        if same(row, row.get("afterSpace"), row.get("target")) and row.get("typed") != row.get("target"):
            group["autocorrected"] += 1
        if same(row, row.get("afterSpace"), row.get("typed")):
            group["preserved"] += 1
        if row.get("offeredHit"):
            group["offered"] += 1
    return groups


def failures(rows: list[dict], limit: int = 25) -> list[tuple[str, str, str]]:
    found = []
    for row in rows:
        if row.get("status") != "OK":
            continue
        committed = (row.get("afterSpace") or "").strip()
        if row.get("case") in PRESERVE_CASES and not same(row, committed, row.get("typed")):
            found.append((row.get("typed"), committed, "испортила верное слово"))
        elif row.get("case") in REPAIR_CASES and not same(row, committed, row.get("target")) \
                and not same(row, committed, row.get("typed")):
            found.append((row.get("typed"), committed, f"ожидалось {row.get('target')}"))
    return found[:limit]


def main() -> int:
    metrics: dict[str, dict[str, dict]] = {}
    any_data = False
    for build in BUILDS:
        metrics[build] = {}
        for language in LANGUAGES:
            report = load_report(build, language)
            rows = report.get("results", [])
            if rows:
                any_data = True
                metrics[build][language] = metric_block(rows, report)

    if not any_data:
        print(f"no reports in {BENCH}; run tools/qa/Invoke-ImeBenchmark.ps1 first", file=sys.stderr)
        return 1

    OUT_JSON.write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8")

    lines = [
        "# Автозамена и контекстные подсказки: сравнительный прогон",
        "",
        "Данные получены реальными касаниями по установленной клавиатуре в тестовом приложении "
        "`:ime-harness` (поле `prose`: `TYPE_TEXT_FLAG_AUTO_CORRECT` и "
        "`IME_FLAG_NO_PERSONALIZED_LEARNING`), без `adb input text` и без подмены движка. "
        "Корпус: `docs/qa/autocorrect-corpus.csv`, 1138 сценариев.",
        "",
        "Перед каждым прогоном данные пакета очищаются (`pm clear`), поэтому сборка стартует с "
        "чистого личного словаря. Скорость набора — 150 мс между клавишами, 200 мс пауза перед "
        "пробелом: слой подсказок над клавишами объединяет перерисовки, и при более быстром "
        "наборе он отстаёт на одну клавишу.",
        "",
        "Определения: **исправлено** — после пробела в поле стоит целевое слово, а набрано было "
        "другое; **ложно исправлено** — опечатка превратилась в третье слово; **сохранено** — "
        "верное слово осталось собой; **recall** — целевое слово вообще появилось среди "
        "показанных над клавишами (это видимое подмножество, а не полный список движка).",
        "",
        "Прогон стратифицированный: корпус упорядочен по типам сценариев, поэтому берётся не "
        "первые N строк, а N строк каждого типа (см. `-Sample` у `Invoke-ImeBenchmark.ps1`). "
        "Число строк в таблице — это фактический размер выборки, а не весь корпус.",
        "",
    ]
    for language, title in (("en", "Английский"), ("ru", "Русский")):
        lines.append(f"## {title}")
        lines.append("")
        lines.extend(build_table(metrics, language))
        lines.append("")
        if language == "ru":
            lines.append(
                "Русская колонка сравнима только с форком по одной причине: русский пакет есть "
                "только в нём. Upstream beta.29 держит пакеты на внешнем сервере — в списке "
                "«Add a language» у него «Downloads 4,4 MB», а офлайн пакета нет. У 4PDA-мода "
                "строка Russian в системном списке языков `enabled=\"false\"` (пакет не установлен, "
                "сервер загрузки недоступен). Переключение раскладки свайпом по пробелу на этих "
                "сборках поэтому не срабатывает, и прогон останавливается на калибровке, а не "
                "показывает нули. Проверка русского на них требует установки пакета вручную и "
                "остаётся открытой — см. [ограничения](known-limitations.md)."
            )
            lines.append("")

    lines.append("## Условия прогона")
    lines.append("")
    lines.append("| Сборка | Язык | Строк | Регистр сложен | Shift доступен |")
    lines.append("|---|---|---|---|---|")
    for build in BUILDS:
        for language in LANGUAGES:
            block = metrics.get(build, {}).get(language)
            if not block:
                continue
            lines.append(
                f"| {LABELS[build]} | {language} | {block['rows_run']} | "
                f"{block['case_folded_rows']} | {'да' if block['shift_available'] else 'нет'} |"
            )
    lines.append("")
    lines.append(
        "«Shift недоступен» — сборка не принимает синтетическое касание по своей клавише Shift "
        "(4PDA-мод принимает его из системной очереди, но не через accessibility-мост), поэтому "
        "такие строки набраны в нижнем регистре и сравнены без учёта регистра. Это ограничение "
        "канала ввода, а не свойство клавиатуры; подробности в [ограничениях](known-limitations.md)."
    )
    lines.append("")

    lines.append("## По типам сценариев, английский")
    lines.append("")
    lines.append("| Тип | Сборка | Строк | Исправлено | Сохранено | Цель в подсказках |")
    lines.append("|---|---|---|---|---|---|")
    for build in BUILDS:
        rows = load_rows(build, "en")
        for case, group in sorted(by_case(rows).items()):
            lines.append(
                f"| {case} | {LABELS[build]} | {group['rows']} | {group['autocorrected']} | "
                f"{group['preserved']} | {group['offered']} |"
            )
    lines.append("")
    lines.append("## По типам сценариев, русский")
    lines.append("")
    lines.append("| Тип | Сборка | Строк | Исправлено | Сохранено | Цель в подсказках |")
    lines.append("|---|---|---|---|---|---|")
    for build in BUILDS:
        rows = load_rows(build, "ru")
        for case, group in sorted(by_case(rows).items()):
            lines.append(
                f"| {case} | {LABELS[build]} | {group['rows']} | {group['autocorrected']} | "
                f"{group['preserved']} | {group['offered']} |"
            )
    lines.append("")
    lines.append("## Примеры неудачных замен")
    lines.append("")
    for language, title in (("en", "Английский"), ("ru", "Русский")):
        for build in BUILDS:
            rows = load_rows(build, language)
            bad = failures(rows)
            if not bad:
                continue
            lines.append(f"**{title}, {LABELS[build]}**")
            for typed, committed, why in bad:
                lines.append(f"- `{typed}` → `{committed}` ({why})")
            lines.append("")
    lines.append("## Сырые данные")
    lines.append("")
    lines.append("`docs/testing/artifacts/audit-2026-11-24/bench/<сборка>-<язык>.json` — по строке на "
                 "сценарий: показанные слова, текст после пробела, отмена, задержки. "
                 "Сводные метрики — `docs/qa/autocorrect-benchmark.json`.")
    lines.append("")
    OUT_MD.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUT_MD}")
    print(f"wrote {OUT_JSON}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
