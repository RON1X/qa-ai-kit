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
seen="|"
for layer in pages steps api db mock model config; do
  files=$(find "$ROOT" -path "*/$layer/*" ! -path "*/tests/*" -name '*.java' | sort)
  [[ -z "$files" ]] && continue
  echo "## $layer"
  for f in $files; do
    case "$seen" in *"|$f|"*) continue ;; esac   # api/model попадает и в api, и в model — печатаем один раз
    seen="$seen$f|"
    cls=$(basename "$f" .java)
    doc=$(grep -m1 -E '^\s*/?\*\*? ' "$f" | sed -E 's#^\s*/?\*+\s*##; s#\s*\*/\s*$##')
    echo
    echo "### $cls"
    [[ -n "$doc" ]] && echo "$doc"
    echo '```'
    # Lombok-модели (@Value): поля = свойства с геттерами и билдером
    if grep -q '^@Value' "$f"; then
      echo "поля: $(grep -P '^\s{4}[\w<>\[\],.? ]+\s+\w+;\s*$' "$f" | sed -E 's/^\s+//; s/;\s*$//' | paste -sd ',' - | sed 's/,/, /g')"
    fi
    # публичные и protected методы классов + методы интерфейсов (у них нет модификатора)
    { grep -P '^\s+(public|protected)\s+(static\s+)?(<[^>]+>\s+)?[\w<>\[\],.? ]+\s+\w+\(.*\)\s*(throws [\w., ]+)?\s*\{\s*$' "$f"
      if grep -qP '^public\s+interface\s' "$f"; then grep -P '^\s{4}[\w<>\[\],.? ]+\s+\w+\(.*\);\s*$' "$f"; fi
    } | sed -E 's/^\s+//; s/\s*\{\s*$//' | grep -vE '^(public )?(static )?(final )?(record|class|interface|enum) ' || true
    echo '```'
  done
  echo
done
