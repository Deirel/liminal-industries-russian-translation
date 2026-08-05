# Чекпоинт 1: исходное состояние

Дата фиксации: 2026-08-05.

## Original

- Версия сборки: `Liminal Industries 1.19.3`, CurseForge file `7397248`.
- Minecraft / Forge / FTB Quests: `1.20.1` / `47.4.13` / `2001.4.13`.
- Манифест: 19 853 записи, 1 731 исходный файл, SHA-256
  `5aed821ddb464e39bdaa1a7e05f90e9c260bf7ee528721a3ee272e63c5ae8c66`.
- Конфигурация источников `sources.json`: SHA-256
  `79f01ec52ce0475709fad760a1900770c98711cee87a6c003504df98e3d8f492`.
- Отдельный состав `manifest.source_files`: SHA-256
  `3903b34a85bd49f19c054cbebbbab7ef1f5c5b1172f72783123cec97695cc5cd`.
- Результат сборки: 7 232 каталожных перевода, 12 621 штатный перевод,
  0 ожидающих записей, 0 ошибок; SHA-256 `build-report.json`
  `0e9fec95b94e2ba4c3f03b9b8ec14d1731a52dd78e51ca47efa8499143d49037`.
- SHA-256 `migration-report.json`:
  `6e2a8daea5500b07f484d7f96ef6a528d3289052c30cd8ae19587f453d13b63f`.

## Rescripted

- Версия сборки: `Liminal Industries - Rescripted 1.19.3 - 7 (ae2 fix)`,
  CurseForge file `8509377`.
- Minecraft / Forge / FTB Quests: `1.20.1` / `47.4.13` / `2001.4.22`.
- Манифест: 22 659 записей, 1 942 исходных файла, SHA-256
  `a54d51926b8c43c671c4301e8af44d9829837fa275cf71534cfea60f43005346`.
- Конфигурация источников `sources.json`: SHA-256
  `79f01ec52ce0475709fad760a1900770c98711cee87a6c003504df98e3d8f492`.
- Отдельный состав `manifest.source_files`: SHA-256
  `0795918e6092f49bf6330c40ca5b461ca134695d9255978c13c20bef686e73b2`.
- Результат сборки: 9 618 каталожных переводов, 13 041 штатный перевод,
  0 ожидающих записей, 0 ошибок; SHA-256 `build-report.json`
  `4d18cf652de307f869e7d22bbfc65492ebb90b969778e7d2f73626c3840895ff`.
- SHA-256 `migration-report.json`:
  `efaf2bc5a6098ea3633a2609881e6b642ea9eae7255742d15d81fce432b6df56`.

## Воспроизводимость и чистота

Оба манифеста повторно построены два раза подряд из указанных экземпляров.
Второй запуск сохранил SHA-256 манифестов и migration-отчётов без изменений.

Фильтр исходных модов исключает общий префикс
`liminal-industries-russian-translation-`. В Original этим одним правилом
исключены translation-JAR и audit-JAR; в исходном каталоге Rescripted таких JAR
нет. Проверка также подтвердила, что исключённые JAR не попали в список
`source_files`.
