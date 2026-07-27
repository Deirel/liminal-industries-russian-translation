# Тексты для CurseForge

JAR и два файла для ручной установки публикуются в одном проекте.

## Проект

Название:

```text
Liminal Industries: Russian Translation
```

Краткое описание:

```text
Russian quest and item-name translation for Liminal Industries - Rescripted.
```

Класс: `Customization`. Лицензия: `All Rights Reserved`.

Описание:

```markdown
# Liminal Industries: Russian Translation

An unofficial Russian translation for Liminal Industries - Rescripted.

This project provides two installation options:

- **Forge Mod** installs both parts from one JAR and backs up a recognized
  original quest book before replacing it.
- **Resource Pack** and **Quest Configs** provide the same translation for
  manual installation.

## Compatibility

The latest supported version of Liminal Industries - Rescripted is **1.19.3 - 7 (AE2 fix)**.

- Minecraft 1.20.1
- Forge 47.4.13
- FTB Quests 2001.4.22

## Forge Mod installation

1. Place the Forge Mod JAR in the profile's `mods` folder.
2. For a dedicated server, install the same JAR on the server.
3. Select Russian in Minecraft.

The mod only replaces the exact supported original quest book. Before doing so,
it creates a backup under `config/liminal_industries_ru/backups`.

## Manual single-player installation

1. Download both files.
2. Place the Resource Pack ZIP in the `resourcepacks` folder. Do not extract it.
3. Stop the game and back up `config/ftbquests/quests`.
4. Extract the Quest Configs archive into the root directory of the modpack profile.
5. Confirm file replacement.
6. Start the game and select Russian.
7. Enable the resource pack in the Resource Packs menu.

## Manual dedicated server installation

Server administrator:

1. Stop the server.
2. Back up the world and `config/ftbquests/quests`.
3. Extract the Quest Configs archive into the server root directory.
4. Confirm file replacement and start the server.

Each player:

1. Place the Resource Pack ZIP in the `resourcepacks` folder. Do not extract it.
2. Select Russian and enable the resource pack.

The server sends the translated quests to players. The resource pack is installed separately and translates item names.

Source code, checksums, and detailed installation instructions are available on GitHub:

https://github.com/Deirel/liminal-industries-russian-translation
```

## Файл Forge Mod

Имя:

```text
Forge Mod - MC 1.20.1 - LI 1.19.3-7 (AE2 fix)
```

Описание:

```markdown
Initial CurseForge Forge-mod release.

- Added Russian item names and FTB Quests translations in one JAR.
- Added exact quest-book compatibility checks and automatic backup.
- Added client and dedicated-server support.
```

## Файл Resource Pack

Имя:

```text
Resource Pack - MC 1.20.1 - LI 1.19.3-7 (AE2 fix)
```

Описание:

```markdown
Initial CurseForge resource-pack release.

- Added Russian names for selected items.
- Added support for Liminal Industries - Rescripted 1.19.3 - 7 (AE2 fix).
- Use the separate Quest Configs download from this project to translate the quest book.
```

## Файл Quest Configs

Имя:

```text
Quest Configs - MC 1.20.1 - LI 1.19.3-7 (AE2 fix)
```

Описание:

```markdown
Initial CurseForge quest-config release.

- Added Russian translations for the FTB Quests chapters.
- Included translated FTB Quests configuration files.
- Added support for Liminal Industries - Rescripted 1.19.3 - 7 (AE2 fix).
- Use the separate Resource Pack download from this project to translate item names.
```
