"""Shared catalog hashing, validation, and SNBT parsing."""

from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any


SCHEMA_VERSION = 1
TECHNICAL_TOKEN_RE = re.compile(
    r"(?:[&§][0-9A-FK-ORa-fk-or]|%(?:\d+\$)?[sdif]|\{image:[^}]+\})"
)


def sha256_bytes(data: bytes) -> str:
    return "sha256:" + hashlib.sha256(data).hexdigest()


def source_hash(source: str) -> str:
    return sha256_bytes(source.encode("utf-8"))


@dataclass
class SnbtParser:
    text: str
    pos: int = 0

    def error(self, message: str) -> ValueError:
        return ValueError(f"{message} at character {self.pos}")

    def skip_space(self) -> None:
        while self.pos < len(self.text):
            if self.text[self.pos].isspace() or self.text[self.pos] == ",":
                self.pos += 1
                continue
            if self.text.startswith("//", self.pos):
                end = self.text.find("\n", self.pos)
                self.pos = len(self.text) if end < 0 else end + 1
                continue
            if self.text[self.pos] == "#":
                end = self.text.find("\n", self.pos)
                self.pos = len(self.text) if end < 0 else end + 1
                continue
            break

    def parse(self) -> Any:
        value = self.parse_value()
        self.skip_space()
        if self.pos != len(self.text):
            raise self.error("unexpected trailing SNBT")
        return value

    def parse_value(self) -> Any:
        self.skip_space()
        if self.pos >= len(self.text):
            raise self.error("expected SNBT value")
        char = self.text[self.pos]
        if char == "{":
            return self.parse_compound()
        if char == "[":
            return self.parse_list()
        if char in "\"'":
            return self.parse_string()
        return self.parse_bare()

    def parse_string(self) -> str:
        quote = self.text[self.pos]
        if quote == '"':
            start = self.pos
            self.pos += 1
            escaped = False
            while self.pos < len(self.text):
                char = self.text[self.pos]
                self.pos += 1
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == quote:
                    return json.loads(self.text[start : self.pos])
            raise self.error("unterminated SNBT string")

        self.pos += 1
        result: list[str] = []
        escaped = False
        while self.pos < len(self.text):
            char = self.text[self.pos]
            self.pos += 1
            if escaped:
                result.append(char)
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                return "".join(result)
            else:
                result.append(char)
        raise self.error("unterminated SNBT string")

    def parse_bare(self) -> str:
        start = self.pos
        while self.pos < len(self.text):
            char = self.text[self.pos]
            if char.isspace() or char in ",]}":
                break
            self.pos += 1
        if self.pos == start:
            raise self.error("expected bare SNBT value")
        return self.text[start : self.pos]

    def parse_key(self) -> str:
        self.skip_space()
        if self.pos < len(self.text) and self.text[self.pos] in "\"'":
            return self.parse_string()
        start = self.pos
        while self.pos < len(self.text):
            char = self.text[self.pos]
            if char == ":" or char.isspace():
                break
            self.pos += 1
        if self.pos == start:
            raise self.error("expected compound key")
        return self.text[start : self.pos]

    def parse_compound(self) -> dict[str, Any]:
        result: dict[str, Any] = {}
        self.pos += 1
        while True:
            self.skip_space()
            if self.pos >= len(self.text):
                raise self.error("unterminated compound")
            if self.text[self.pos] == "}":
                self.pos += 1
                return result
            key = self.parse_key()
            self.skip_space()
            if self.pos >= len(self.text) or self.text[self.pos] != ":":
                raise self.error(f"expected ':' after {key!r}")
            self.pos += 1
            if key in result:
                raise self.error(f"duplicate compound key {key!r}")
            result[key] = self.parse_value()

    def parse_list(self) -> list[Any]:
        result: list[Any] = []
        self.pos += 1
        self.skip_space()
        if (
            self.pos + 1 < len(self.text)
            and self.text[self.pos] in "BIL"
            and self.text[self.pos + 1] == ";"
        ):
            self.pos += 2
        while True:
            self.skip_space()
            if self.pos >= len(self.text):
                raise self.error("unterminated list")
            if self.text[self.pos] == "]":
                self.pos += 1
                return result
            result.append(self.parse_value())


def parse_snbt(path: Path) -> dict[str, Any]:
    parsed = SnbtParser(path.read_text(encoding="utf-8")).parse()
    if not isinstance(parsed, dict):
        raise ValueError(f"{path}: root must be a compound")
    return parsed


def require_string(owner: dict[str, Any], key: str, context: str) -> str:
    value = owner.get(key)
    if not isinstance(value, str) or not value:
        raise ValueError(f"{context}: missing non-empty string {key!r}")
    return value


def validate_catalog(catalog: dict[str, Any]) -> None:
    if catalog.get("schema_version") != SCHEMA_VERSION:
        raise ValueError("wrong catalog schema version")
    seen: set[tuple[str, str]] = set()
    for logical_id, variants in catalog.get("entries", {}).items():
        if not isinstance(variants, list) or not variants:
            raise ValueError(f"{logical_id}: variants must be a non-empty list")
        for variant in variants:
            source = variant.get("source")
            translation = variant.get("translation")
            digest = variant.get("source_hash")
            if not isinstance(source, str) or not source:
                raise ValueError(f"{logical_id}: empty source")
            if not isinstance(translation, str) or not translation:
                raise ValueError(f"{logical_id}: empty translation")
            if digest != source_hash(source):
                raise ValueError(f"{logical_id}: source hash mismatch")
            source_tokens = sorted(TECHNICAL_TOKEN_RE.findall(source))
            translation_tokens = sorted(TECHNICAL_TOKEN_RE.findall(translation))
            if source_tokens != translation_tokens:
                raise ValueError(f"{logical_id}: technical tokens differ")
            pair = (logical_id, digest)
            if pair in seen:
                raise ValueError(f"{logical_id}: duplicate ID + source_hash")
            seen.add(pair)
