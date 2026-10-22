#!/usr/bin/env bash
# Механические запреты, которые не надо доверять памяти модели (идея из Superpowers/writing-skills:
# если правило проверяется регуляркой — автоматизируй его, а инструкцию оставь для суждений).
# Проверяет изменения относительно базовой ветки. Запуск: локально перед коммитом, в CI на MR,
# или как хук агента после записи файла.
#
# Использование: scripts/guard-tests.sh [базовая-ветка=origin/master]
set -uo pipefail

BASE="${1:-origin/master}"
FAIL=0

# Изменения ветки относительно базы + незакоммиченные. Функции всегда возвращают 0 (важно при pipefail).
changed() { { git diff --name-only --diff-filter=AM "$BASE"...HEAD -- "$@" 2>/dev/null; git diff --name-only --diff-filter=AM -- "$@"; } || true; }
added_lines() { { git diff "$BASE"...HEAD -- "$@" 2>/dev/null; git diff -- "$@"; } | grep -E '^\+[^+]' || true; }
removed_lines() { { git diff "$BASE"...HEAD -- "$@" 2>/dev/null; git diff -- "$@"; } | grep -E '^-[^-]' || true; }

report() { echo "❌ $1"; FAIL=1; }

# 1. Паузы вместо ожидания условия
if added_lines 'src/test/java' | grep -nE 'Thread\.sleep|waitForTimeout|LoadState\.NETWORKIDLE'; then
  report "Паузы в тестах. Используйте assertThat(locator) или await().untilAsserted(...)."
fi

# 2. Отключённые тесты
if added_lines 'src/test/java' | grep -nE '@Disabled|@Ignore'; then
  report "Тест отключён. Если это баг продукта — заведите баг и согласуйте отключение с человеком."
fi

# 3. Опасный SQL
for f in $(changed 'src/test/resources/sql' | sort -u); do
  [[ -f "$f" ]] || continue
  if grep -qiE '\btruncate\b' "$f"; then report "TRUNCATE в $f"; fi
  if grep -qiE '\b(delete|update)\b' "$f" && ! grep -qE "AT-%" "$f"; then
    report "DELETE/UPDATE без предохранителя по префиксу AT- в $f"
  fi
done

# 4. Удалённые проверки — не запрет, но требует внимания человека
REMOVED=$(removed_lines 'src/test/java' | grep -cE 'assertThat\(|then\(|verifyThat\(|awaitSingleRequest\(' || true)
if [[ "${REMOVED:-0}" -gt 0 ]]; then
  echo "⚠️  Удалено проверок: $REMOVED. Убедитесь, что это не ослабление теста ради зелёного прогона."
fi

# 5. Выдуманный API мок-сервера
if added_lines 'src/test/java' | grep -nE 'TODO\(SberMock\)'; then
  report "Добавлены TODO(SberMock): адаптер к SberMock заполняет человек по документации."
fi

[[ "$FAIL" -eq 0 ]] && echo "✅ guard-tests: нарушений нет"
exit "$FAIL"
