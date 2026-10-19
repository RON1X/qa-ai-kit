#!/usr/bin/env bash
# «RAG для бедных»: оглавление готовых Page Object-ов, шагов, DAO и стабов с сигнатурами публичных методов.
# Агент читает один короткий файл вместо всего репозитория и переиспользует готовое вместо того,
# чтобы писать дубли. Идея — из опыта Сбера (подстановка ближайших шагов проекта в промпт).
#
# Использование: scripts/index-framework.sh [корень-пакета] > docs/framework-index.md
# Перегенерировать после каждого мержа (можно в CI).
set -euo pipefail

ROOT="${1:-src/test/java}"

echo "# Оглавление фреймворка (сгенерировано $(date +%F), не править руками)"
echo
for layer in pages steps db mock model; do
  files=$(find "$ROOT" -path "*/$layer/*" -name '*.java' | sort)
  [[ -z "$files" ]] && continue
  echo "## $layer"
  for f in $files; do
    cls=$(basename "$f" .java)
    doc=$(grep -m1 -E '^\s*/?\*\*? ' "$f" | sed -E 's#^\s*/?\*+\s*##; s#\s*\*/\s*$##')
    echo
    echo "### $cls"
    [[ -n "$doc" ]] && echo "$doc"
    echo '```'
    # публичные методы классов + методы интерфейсов (у них нет модификатора public)
    { grep -P '^\s+public\s+(static\s+)?(<[^>]+>\s+)?[\w<>\[\],.? ]+\s+\w+\(.*\)\s*(throws [\w., ]+)?\s*\{\s*$' "$f"
      if grep -qP '^public\s+interface\s' "$f"; then grep -P '^\s{4}[\w<>\[\],.? ]+\s+\w+\(.*\);\s*$' "$f"; fi
    } | sed -E 's/^\s+//; s/\s*\{\s*$//' | grep -vE '^(public )?(static )?(final )?(record|class|interface|enum) ' || true
    echo '```'
  done
  echo
done
