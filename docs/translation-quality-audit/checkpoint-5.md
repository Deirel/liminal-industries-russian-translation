# Чекпоинт 5: проверка Original

Дата проверки: 2026-08-05.

Проверены все 19 853 записи эффективного реестра Original. Решение Rescripted
переиспользовано только для 14 687 точных совпадений `ID`, `source_hash`,
английского текста, эффективного русского текста и полного JSON-контекста.
Точный критерий воспроизводит `build_review_reuse.py --check`.

Оставшиеся 5 160 видимых записей прочитаны отдельно в Original-контексте:

- 3 711 строк квестов и книг;
- 1 008 названий предметов и блоков namespace A–M;
- 441 название предмета или блока namespace N–Z.

Для каждой видимой записи сохранён индивидуальный `PASS` или `CHANGE` с
ревьюером и методом. Шесть исходных полей, заменённых структурными overrides,
сохранены как невидимые. Итог: 19 574 `PASS`, 279 `CHANGE`, из которых 170
точно переиспользованы из Rescripted, а 109 относятся к отдельно прочитанному
срезу Original.

Все 109 новых рекомендаций повторно проверены независимым
`gpt-5.6-terra / medium`: 109 одобрены, 0 отклонены, 3 отредактированы до
утверждения. У переиспользованных 170 рекомендаций сохранён исходный
независимый review trace Rescripted. Непроверенных изменений и незакрытых
замечаний нет.

Основные артефакты:

- `work/quality-review.tsv`: SHA-256
  `f8b8cb2b175b4937f37a058f844b9335ae5823533054824a9c68022479d1ab37`;
- `work/quality-review-summary.json`: SHA-256
  `104beeabd5830feb17240200cd9b456e22266a70e853c8a207255108d04b12ec`;
- `work/quality-audit-2026-08-05/reuse-summary.json`: SHA-256
  `2c2762e7d19bd69b24d7846041334384f838ad6a069ce4402ca8773e85fc57d5`;
- `work/quality-audit-2026-08-05/approved-new-changes.tsv`: SHA-256
  `ba5b0e83016d9bc187a0ebec53f9b9f1abdac61c62297868d8b20a499be925f6`;
- `work/quality-audit-2026-08-05/change-review-summary.json`: SHA-256
  `7f8502b7e3fd4e1153dc940ec42cddc72cf7f888fe033d9dfc7036b3f7f504f3`.

`build_quality_review.py --check` подтверждает полное покрытие и ноль
неутверждённых `CHANGE`. Команда
`PYTHONPATH=scripts python3 -m unittest discover -s scripts/tests -p 'test_*.py'`
выполняет все 61 тест без ошибок.
