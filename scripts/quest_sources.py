"""Extract translatable FTB Quests fields from SNBT text."""

from __future__ import annotations

import json
import re


FIELDS = {"title", "subtitle", "description"}
TOKEN_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_.+-]*")


def parse_string(source: str, start: int) -> tuple[int, str]:
    if source[start] != '"':
        raise ValueError(f"expected string at character {start}")

    escaped = False
    pos = start + 1
    while pos < len(source):
        char = source[pos]
        if escaped:
            escaped = False
        elif char == "\\":
            escaped = True
        elif char == '"':
            literal = source[start : pos + 1]
            try:
                return pos + 1, json.loads(literal)
            except json.JSONDecodeError as exc:
                raise ValueError(
                    f"unsupported SNBT string at character {start}: {exc}"
                ) from exc
        pos += 1

    raise ValueError(f"unterminated string at character {start}")


def skip_space(source: str, pos: int) -> int:
    while pos < len(source) and source[pos].isspace():
        pos += 1
    return pos


def description_strings(source: str, start: int) -> tuple[int, list[dict]]:
    if source[start] != "[":
        raise ValueError(f"expected description list at character {start}")

    records: list[dict] = []
    square_depth = 1
    curly_depth = 0
    pos = start + 1

    while pos < len(source) and square_depth:
        char = source[pos]
        if char == '"':
            end, value = parse_string(source, pos)
            if square_depth == 1 and curly_depth == 0:
                records.append({"start": pos, "end": end, "original": value})
            pos = end
            continue
        if char == "[":
            square_depth += 1
        elif char == "]":
            square_depth -= 1
        elif char == "{":
            curly_depth += 1
        elif char == "}":
            curly_depth -= 1
        pos += 1

    if square_depth:
        raise ValueError(f"unterminated description list at character {start}")
    return pos, records


def extract_records(source: str) -> list[dict]:
    records: list[dict] = []
    pos = 0

    while pos < len(source):
        if source[pos] == '"':
            pos, _ = parse_string(source, pos)
            continue

        match = TOKEN_RE.match(source, pos)
        if not match:
            pos += 1
            continue

        field = match.group()
        pos = match.end()
        if field not in FIELDS:
            continue

        value_start = skip_space(source, pos)
        if value_start >= len(source) or source[value_start] != ":":
            continue
        value_start = skip_space(source, value_start + 1)

        if field == "description":
            if value_start >= len(source) or source[value_start] != "[":
                raise ValueError(
                    f"description is not a string list at character {value_start}"
                )
            pos, found = description_strings(source, value_start)
            for record in found:
                record["field"] = field
                records.append(record)
        else:
            if value_start >= len(source) or source[value_start] != '"':
                raise ValueError(
                    f"{field} is not a string at character {value_start}"
                )
            end, value = parse_string(source, value_start)
            records.append(
                {
                    "field": field,
                    "start": value_start,
                    "end": end,
                    "original": value,
                }
            )
            pos = end

    return records
