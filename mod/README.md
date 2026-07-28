# Liminal Industries Russian Translation Mod

Клиентский Forge-мод с русским переводом Liminal Industries и
Liminal Industries - Rescripted. Для каждой версии модпака собирается отдельный
JAR с собственным payload квестов и языковых ресурсов.

## Сборка

Требуется JDK 17:

```sh
./gradlew clean test build -PtranslationVersion=1.19.3-original
./gradlew clean test build -PtranslationVersion=1.19.3-7-ae2-fix
```

Gradle читает `../translation-versions/<version>/build-config.json` и подключает
`../translation-versions/<version>/payload`. Готовый файл создаётся в
`build/libs`.

Переводы названий загружаются как обычные ресурсы Forge. Текст FTB Quests
подменяется на клиенте только во время отображения и никогда не записывается в
каталог конфигурации. Перед применением мод сверяет полный набор ID объектов
книги с payload выбранной версии.

Полное техническое описание находится в
[`TECHNICAL_SPEC.md`](TECHNICAL_SPEC.md).
