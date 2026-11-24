#!/usr/bin/env python3
"""Compares a recall dump against test/data/recall_top8_golden.txt.

The dump prints ranked words with hex-float scores. Bit-exact equality is not a property we can
require across toolchains: the ranker is float arithmetic plus sqrtf, so a different libm/compiler
shifts the last bits of a score, and candidates whose scores are identical (or within noise) may
come out in a different order because the sort is not stable. That is not a ranking regression.

This comparator therefore checks everything a ranking regression would actually change:
  * the same traces, in the same order, with the same KDB/corpus labels;
  * the same candidate set for every trace (a frequency/index/prune change alters it);
  * each candidate's score within SCORE_TOL of the golden value (a table/index change does not
    stay inside this band);
  * the produced list actually sorted by score, so the order still means "best first".

Usage: compare_recall_golden.py <golden> <produced>
Exit 0 on success; prints the first mismatches and exits 1 otherwise.
"""
import sys

SCORE_TOL = 1e-4          # relative, against max(1, |golden|)
TIE_TOL = 1e-4            # scores closer than this are a tie: order and cutoff are arbitrary
MAX_REPORTED = 10


def parse(line):
    head, sep, rest = line.partition(':')
    if not sep:
        raise ValueError(f"malformed line: {line[:80]!r}")
    tokens = []
    for token in rest.split():
        word, sep2, score = token.rpartition('=')
        if not sep2:
            raise ValueError(f"malformed token {token!r} in {line[:60]!r}")
        tokens.append((word, float.fromhex(score)))
    return head.strip(), tokens


def main(golden_path, produced_path):
    golden = [l for l in open(golden_path, encoding='utf-8').read().splitlines() if l]
    produced = [l for l in open(produced_path, encoding='utf-8').read().splitlines() if l]
    problems = []

    if len(golden) != len(produced):
        problems.append(f"trace count: golden {len(golden)}, produced {len(produced)}")

    reorders = 0
    boundary_swaps = 0
    worst = 0.0
    for index, (golden_line, produced_line) in enumerate(zip(golden, produced)):
        try:
            golden_head, golden_tokens = parse(golden_line)
            produced_head, produced_tokens = parse(produced_line)
        except ValueError as error:
            problems.append(f"line {index + 1}: {error}")
            continue
        if golden_head != produced_head:
            problems.append(f"line {index + 1}: trace {produced_head!r} != golden {golden_head!r}")
            continue

        golden_words = [w for w, _ in golden_tokens]
        produced_words = [w for w, _ in produced_tokens]
        golden_score = dict(golden_tokens)
        golden_words = [w for w, _ in golden_tokens]
        produced_words = [w for w, _ in produced_tokens]
        replaced = set(golden_words) ^ set(produced_words)
        if replaced:
            # A different candidate at the cutoff is legal only inside a tie: the recall prunes to
            # a fixed top-8, so candidates scored within noise can swap the last seat.
            involved = [s for w, s in golden_tokens + produced_tokens if w in replaced]
            spread = (max(involved) - min(involved)) / max(1.0, min(involved))
            if spread > TIE_TOL:
                missing = sorted(set(golden_words) - set(produced_words))
                extra = sorted(set(produced_words) - set(golden_words))
                problems.append(
                    f"{produced_head}: candidate set changed "
                    f"(missing {missing}, unexpected {extra}, score spread {spread:.3g})")
                continue
            boundary_swaps += 1

        for word, score in produced_tokens:
            expected = golden_score.get(word)
            if expected is None:      # a tolerated tie-boundary substitution
                continue
            drift = abs(score - expected) / max(1.0, abs(expected))
            worst = max(worst, drift)
            if drift > SCORE_TOL:
                problems.append(
                    f"{produced_head}: {word} scored {score!r}, golden {expected!r} "
                    f"(drift {drift:.3g} > {SCORE_TOL:g})")
        if produced_words != golden_words:
            reorders += 1
            # Only a tie may legally reorder: the scores involved must be indistinguishable.
            scores = [s for _, s in produced_tokens]
            if any(b < a for a, b in zip(scores, scores[1:])):
                problems.append(f"{produced_head}: not sorted by score: {produced_words}")

    for problem in problems[:MAX_REPORTED]:
        print(f"  FAIL {problem}")
    if len(problems) > MAX_REPORTED:
        print(f"  ... and {len(problems) - MAX_REPORTED} more")

    if problems:
        return 1
    print(f"  OK: {len(produced)} traces; max score drift {worst:.3g}; "
          f"{reorders} tie reorder(s) and {boundary_swaps} tie-boundary swap(s) tolerated")
    return 0


if __name__ == '__main__':
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1], sys.argv[2]))
