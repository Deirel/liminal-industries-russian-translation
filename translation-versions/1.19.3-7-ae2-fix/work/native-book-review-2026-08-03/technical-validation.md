# Technical validation: READY

Validated the three review TSV files against the current
`1.19.3-7-ae2-fix/manifest.json` and the effective native Russian resources in
the Rescripted instance (non-project mod JARs, then KubeJS and directory
resource-pack language overlays).

## Passed checks

- Exactly 1,898 expected `NATIVE_RU` book records are covered once: 1,241 in
  `botania.tsv`, 648 in `other-patchouli.tsv`, and 9 in
  `immersive-engineering.tsv`.
- Rows preserve manifest order within each deliberate TSV partition.
- Every row's `id`, English `source`, and `source_hash` match the manifest;
  all manifest hashes recompute correctly.
- TSV structure is valid: the required ten columns are present and there are
  no embedded tabs or line breaks in fields.
- Verdicts are valid: 1,504 `PASS`, 366 `CHANGE`, and 28
  `LENGTH_EXCEPTION`. `PASS` rows have empty recommendation and recommendation
  length fields; every `CHANGE` is at most 1.10 times the recorded visible
  length; every `LENGTH_EXCEPTION` is over that limit.
- Patchouli markers, format codes, placeholders, links, and numeric literals
  from the English source are preserved in every recommendation. Decimal
  separators and grouped thousands were compared as equivalent numeric
  literals; numbers inside Patchouli markup were excluded.

## Reconciliation result

The four Actually Additions rows previously reported as provenance drift were
mechanically updated to the effective installed native values and were checked
again. All 1,898 rows now reproduce their native Russian source exactly.

No overrides or payload files were changed by this validation.
