# Technical validation: native book review

## Result

**READY**

All requested objective checks pass: exact corpus coverage and manifest
identity, installed native text, verdict payloads, visible-length metrics,
technical-token fidelity, and TSV structure.

## Inputs

- Manifest SHA-256: `31163d547354816e2e1f8ea9a25879296952f2d30646b58e4fa679a75d7ab9bf`
- `botania.tsv`: `4e37b7e710e0b70b18d4866d9af0c6028e6e1ab1c887acb74abecf922d50bb74`
- `thermal-enderio.tsv`: `d6bc0e4eede8cf8ecc3bd911ef38664a06bdc2d43df6a61d2376ac5d0b752639`
- `immersive-engineering.tsv`: `9c5a85ced9a04e81ea5af4d86bb28649bdabe7b289f30dc78583a98fc8344dc3`

Installed native resources were read directly from the Original instance:

- Botania JAR SHA-256: `ce41e103686a59288398e0745d4ac9181a5203029f308916a264acc38981b6da`
- Thermal Foundation JAR SHA-256: `8cb610abb3c835b47f9b4ec19be6a54104d1f61230b67a9d5bfd097c5ac45823`
- nested Thermal Core JAR SHA-256: `c10f1ad221cb0719a6627c9ceb1d104f03631ba41bb401e10ec86fd21d7d96f7`
- Ender IO JAR SHA-256: `a7f4a7f00f443e22108a50b7133473cb98445c5e1617084f0d929ee83a634faf`
- Immersive Engineering JAR SHA-256: `061dd0e8e066dc39277736d5607a47a18fee4aff428de3363882f96e5c405aaa`

## Checks

1. **PASS - exact coverage and partitioning.** The manifest contains exactly
   1,882 `NATIVE_RU` records for the book source types: 1,873 `patchouli` and 9
   `immersive_engineering_manual`. The TSV union contains every record exactly
   once: Botania 1,231; Thermal 640 plus Ender IO 2; IE 9. There are no missing,
   extra, or duplicate IDs.
2. **PASS - manifest identity and order.** Every `id`, `source_hash`, and full
   `source` equals the current manifest value. Row order is exactly the manifest
   order inside each partition.
3. **PASS - installed native Russian.** All 1,882 `native_translation` values
   exactly equal the installed `ru_ru` values. This includes Thermal JSON and
   language values from the nested Thermal Core archive and IE values from
   `assets/immersiveengineering/lang/ru_ru.json`.
4. **PASS - verdict payload contract.** All verdict values belong to `PASS`,
   `CHANGE`, or `LENGTH_EXCEPTION`. All 1,488 `PASS` rows have empty
   `recommendation`, `recommended_visible_length`, and `length_ratio` fields;
   all 394 non-PASS rows contain a full recommendation and both metrics.
5. **PASS - uniform visible-length metrics.** Native visible lengths are
   correct for all 1,882 rows. For every `CHANGE` and `LENGTH_EXCEPTION`,
   recommended length and the three-decimal ratio are correct. All 368
   `CHANGE` rows have a ratio at most `1.100`; all 26 `LENGTH_EXCEPTION` rows
   have a ratio greater than `1.100`. The normalization uniformly removes
   Patchouli `$(...)` markers, Minecraft `section-sign + code` format codes, and
   printf placeholders without collapsing ordinary whitespace.
6. **PASS - technical fidelity.** For all non-PASS recommendations, Patchouli
   markers and link targets, placeholders, format codes, and numeric values are
   preserved. Numeric comparison normalizes decimal comma versus point,
   thousands separators, `k` notation, and attached units or multiplier
   notation; it does not treat translated units as changed values.
7. **PASS - TSV structure.** Every file parses as TSV with exactly 10 columns
   per row. Required fields are present; there are no extra fields, malformed
   rows, or duplicate IDs.

## `65_items.json`

The four records are present in manifest order and exactly match the manifest
and installed nested Thermal Core `ru_ru`:

- `/name`: `PASS`, visible length 22.
- `/pages/0/text`: `CHANGE`, 377 -> 321, ratio `0.851`; values 2.5 and 50% are
  preserved as Russian `2,5` and `50%`.
- `/pages/1/text`: `CHANGE`, 132 -> 128, ratio `0.970`.
- `/pages/1/title`: `PASS`, visible length 14.

There are no marker, link, placeholder, format-code, or number-fidelity errors
in these four records. This is a data-level validation only; it does not claim
a runtime layout result.
