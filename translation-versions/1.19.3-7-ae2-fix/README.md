# Liminal Industries - Rescripted 1.19.3 - 7 (AE2 fix)

Источник:

- CurseForge project ID: `1532243`
- CurseForge file ID: `8509377`
- Minecraft: `1.20.1`
- Forge: `47.4.13`
- FTB Quests: `2001.4.22`

Манифест и дельта создаются из чистой копии модпака:

```sh
python3 scripts/build_version_delta.py \
  --instance-root "$HOME/Projects/minecraft-industrial-backrooms/data" \
  --sklauncher-manifest "$HOME/Library/Application Support/sklauncher/manifests/liminal-industries-rescripted.json" \
  --item-hints translation-versions/1.19.3-7-ae2-fix/item-source-hints.tsv \
  --version-slug 1.19.3-7-ae2-fix
```

`item-source-hints.tsv` фиксирует проверенные runtime translation keys и
английские имена для вариантов, которые ProbeJS перечисляет, но статические
lang-файлы не описывают.

Дополнительные ключи, обнаруженные только игровым runtime-аудитом, хранятся в
`runtime-audit-overrides.json`. Сборщик добавляет их только в payload
Rescripted-версии. Schema 2 также содержит `accepted_same_as_english`:
исчерпывающую карту проверенных собственных имён и их точных английских
значений. Изменившийся оригинал требует нового решения.

Готовый payload и JAR собираются командами:

```sh
python3 scripts/build_version_resources.py \
  --version 1.19.3-7-ae2-fix \
  --instance-root "$HOME/Projects/minecraft-industrial-backrooms/data"

cd mod
./gradlew clean test build -PtranslationVersion=1.19.3-7-ae2-fix
```

После утверждения всех переводов повторное построение манифеста должно дать
`pending: 0`.
