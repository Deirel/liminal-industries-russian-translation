"""Load version-local runtime translations and audit policy."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class RuntimeAuditOverrides:
    translations: dict[str, dict[str, str]]
    accepted_same_as_english: dict[str, str]


def load_runtime_audit_overrides(path: Path) -> RuntimeAuditOverrides:
    if not path.exists():
        return RuntimeAuditOverrides({}, {})

    document = json.loads(path.read_text(encoding="utf-8"))
    translations = document.get("translations")
    accepted = document.get("accepted_same_as_english", {})
    if (
        document.get("schema") not in {1, 2}
        or not isinstance(translations, dict)
        or any(
            not isinstance(namespace, str)
            or not isinstance(values, dict)
            or any(
                not isinstance(key, str)
                or not isinstance(value, str)
                or not value
                for key, value in values.items()
            )
            for namespace, values in translations.items()
        )
        or not isinstance(accepted, dict)
        or any(
            not isinstance(key, str)
            or not key
            or not isinstance(value, str)
            or not value
            for key, value in accepted.items()
        )
        or (document.get("schema") == 1 and accepted)
    ):
        raise ValueError("invalid runtime audit overrides")

    return RuntimeAuditOverrides(
        translations,
        accepted,
    )
