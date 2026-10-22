#!/usr/bin/env bash
# Устанавливает инструкции для ИИ-агента (папка instructions/) в репозиторий автотестов.
#
# Первая установка:   ./install.sh <путь-к-репозиторию-автотестов>
#   Копирует всё, чего ещё нет. Существующие файлы не трогает.
#
# Обновление набора:  git pull && ./install.sh <путь> --update
#   Дополнительно перезаписывает «библиотечные» файлы — скиллы, команды, субагентов, скрипты.
#   Никогда не трогает то, что вы заполняете под свой проект: AGENTS.md, docs/, .qwen/settings*.
#   Если вы правили скилл под себя — перед --update посмотрите diff, правки будут перезаписаны.
set -euo pipefail

SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/instructions"
TARGET="${1:?Укажите путь к репозиторию автотестов: ./install.sh ../my-autotests [--update]}"
MODE="${2:-}"

[[ -d "$TARGET" ]] || { echo "❌ Папка $TARGET не найдена"; exit 1; }
[[ -z "$MODE" || "$MODE" == "--update" ]] || { echo "❌ Неизвестный флаг $MODE (есть только --update)"; exit 1; }
TARGET="$(cd "$TARGET" && pwd)"

# Не копируем: то, что читает человек (README, guides/, chat/), и пример оглавления (генерируется под проект).
is_excluded() {
  case "$1" in
    README.md|docs/framework-index.md|guides/*|chat/*) return 0 ;;
    *) return 1 ;;
  esac
}

# Библиотечные файлы — их обновляет --update.
is_library() {
  case "$1" in
    .qwen/skills/*|.qwen/commands/*|.qwen/agents/*|.qwen/output-styles/*|scripts/*) return 0 ;;
    *) return 1 ;;
  esac
}

added=(); updated=(); kept=()
while IFS= read -r -d '' file; do
  rel="${file#"$SRC"/}"
  is_excluded "$rel" && continue
  dst="$TARGET/$rel"
  if [[ ! -e "$dst" ]]; then
    mkdir -p "$(dirname "$dst")"
    cp -p "$file" "$dst"
    added+=("$rel")
  elif [[ "$MODE" == "--update" ]] && is_library "$rel"; then
    if ! cmp -s "$file" "$dst"; then
      cp -p "$file" "$dst"
      updated+=("$rel")
    fi
  elif ! cmp -s "$file" "$dst"; then
    kept+=("$rel")
  fi
done < <(find "$SRC" -type f -print0 | sort -z)

echo "Установка в $TARGET"
echo "  добавлено:  ${#added[@]}"
echo "  обновлено:  ${#updated[@]}"
echo "  оставлено ваших версий: ${#kept[@]}"
for f in ${updated[@]+"${updated[@]}"}; do echo "    ↻ $f"; done
for f in ${kept[@]+"${kept[@]}"}; do echo "    = $f"; done

if [[ ${#added[@]} -gt 0 && -f "$TARGET/AGENTS.md" ]]; then
  echo
  echo "Дальше:"
  echo "  1. cp .qwen/settings.example.json .qwen/settings.json и впишите адрес и id моделей"
  echo "  2. grep -rn 'ЗАПОЛНИТЬ' AGENTS.md docs .qwen   — заполнить под свой проект"
  echo "  3. scripts/index-framework.sh > docs/framework-index.md"
  echo "  Подробно: instructions/README.md в репозитории qa-ai-kit"
fi
