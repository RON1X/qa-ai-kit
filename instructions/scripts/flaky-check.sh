#!/usr/bin/env bash
# Прогоняет тест N раз подряд и считает падения. Флаки-тест не сдаётся.
# Использование: scripts/flaky-check.sh <Класс[#метод]> [N=3]
set -uo pipefail

TEST="${1:?Укажите тест, например CreatePilotTest или CreatePilotTest#createsPilotWithValidData}"
RUNS="${2:-3}"
FAILED=0
mkdir -p target

for i in $(seq 1 "$RUNS"); do
  if mvn -q test -Dtest="$TEST" > "target/flaky-run-$i.log" 2>&1; then
    echo "прогон $i/$RUNS: ✅"
  else
    echo "прогон $i/$RUNS: ❌  (лог: target/flaky-run-$i.log)"
    FAILED=$((FAILED + 1))
  fi
done

echo "Итог: $((RUNS - FAILED))/$RUNS зелёных"
[[ "$FAILED" -eq 0 ]]
