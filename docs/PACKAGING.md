# Сборка и публикация

Для каждой версии модпака собирается отдельный JAR:

| Версия | FTB Quests | Артефакт |
| --- | --- | --- |
| `1.19.3-original` | `2001.4.13` | `liminal-industries-russian-translation-1.19.3-original.jar` |
| `1.19.3-7-ae2-fix` | `2001.4.22` | `liminal-industries-russian-translation-1.19.3-7-ae2-fix.jar` |

## Payload

Готовые ресурсы каждой версии находятся в
`translation-versions/<version>/payload`. Они строятся только из манифеста
выбранной версии и глобального каталога:

```sh
python3 scripts/build_version_resources.py \
  --version 1.19.3-original \
  --instance-root "$HOME/Library/Application Support/sklauncher/instances/liminal-industries"
```

Для проверки без изменения файлов добавьте `--check`.

## Сборка мода

Требуется JDK 17. Из каталога `mod/` выполните одну из команд:

```sh
./gradlew clean test build -PtranslationVersion=1.19.3-original
./gradlew clean test build -PtranslationVersion=1.19.3-7-ae2-fix
```

Gradle читает `build-config.json` выбранной версии, подключает соответствующий
payload и подставляет правильные версию артефакта, описание и диапазон
FTB Quests в `META-INF/mods.toml`.

Готовый JAR создаётся в `mod/build/libs`. Команда `clean` удаляет предыдущий
артефакт, поэтому релизы собираются и проверяются по отдельности.

## Проверка релиза

1. Перестройте манифест и убедитесь, что `pending` равен нулю.
2. Пересоберите payload и повторите команду с `--check`.
3. Выполните version-specific Gradle-сборку.
4. Проверьте имя JAR, версию и диапазон FTB Quests в `META-INF/mods.toml`.
5. Установите JAR в чистый профиль соответствующего модпака.
6. Проверьте названия предметов и все главы квестов.
7. Выполните runtime-аудит названий предметов.
8. Переключите язык и удалите JAR: должен отображаться оригинальный текст, а
   файлы квестов не должны измениться.
9. Убедитесь, что в Git нет JAR-файлов, журналов, миров и архивов модпака.
