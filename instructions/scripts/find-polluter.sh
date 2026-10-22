#!/usr/bin/env bash
# Поиск теста-«загрязнителя»: TARGET проходит один, но падает после какого-то другого класса.
# Идея из Superpowers (systematic-debugging/find-polluter.sh), переделано под Maven + JUnit 5.
#
# Использование: scripts/find-polluter.sh <ЦелевойКласс> [шаблон-кандидатов]
# Пример:        scripts/find-polluter.sh CreatePilotTest 'src/test/java/**/tests/**/*Test.java'
#
# Каждый кандидат запускается в паре с целевым классом последовательно (без параллельности).
set -euo pipefail

TARGET="${1:?Укажите целевой класс, например CreatePilotTest}"
PATTERN="${2:-src/test/java/**/*Test.java}"

SERIAL="-Djunit.jupiter.execution.parallel.enabled=false"

echo "Проверяю, что $TARGET проходит в одиночку..."
if ! mvn -q test -Dtest="$TARGET" $SERIAL > /dev/null 2>&1; then
  echo "❌ $TARGET падает и в одиночку — это не загрязнение. Разбирайте сам тест."
  exit 2
fi

shopt -s globstar nullglob
CANDIDATES=()
for f in $PATTERN; do
  name="$(basename "$f" .java)"
  [[ "$name" != "$TARGET" ]] && CANDIDATES+=("$name")
done
echo "Кандидатов: ${#CANDIDATES[@]}"

i=0
for c in "${CANDIDATES[@]}"; do
  i=$((i + 1))
  echo "[$i/${#CANDIDATES[@]}] $c -> $TARGET"
  # Сначала кандидат, затем целевой класс — без уборки стенда между ними, как в общем прогоне.
  mvn -q test -Dtest="$c" $SERIAL > /dev/null 2>&1 || true
  if ! mvn -q test -Dtest="$TARGET" $SERIAL > /dev/null 2>&1; then
    echo ""
    echo "🎯 Найден загрязнитель: $c"
    echo "   После него $TARGET падает. Ищите в $c данные или настройки, которые он оставляет:"
    echo "   незарегистрированная уборка, глобальные стабы, изменённые справочники, общий пользователь."
    exit 1
  fi
done

echo "✅ Загрязнитель среди кандидатов не найден. Проверьте параллельный запуск и общие данные стенда."
