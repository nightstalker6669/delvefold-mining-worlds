#!/usr/bin/env python3
"""Generate Delvefold's compressed structure NBT assets from deterministic JSON blueprints."""

from __future__ import annotations

import gzip
import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools" / "landmark_templates.json"
OUTPUT = ROOT / "src" / "main" / "resources" / "data" / "delvefold" / "structure" / "landmarks"
GAME_TEST_OUTPUT = (ROOT / "src" / "main" / "resources" / "data" / "delvefold"
                    / "structure" / "gametest" / "living_geology_reservation.nbt")
DATA_VERSION = 3955  # Minecraft Java 1.21.1
MAX_HORIZONTAL_SPAN = 96


def utf(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def named(tag_type: int, name: str, payload: bytes) -> bytes:
    return bytes((tag_type,)) + utf(name) + payload


def int_payload(value: int) -> bytes:
    return struct.pack(">i", value)


def long_payload(value: int) -> bytes:
    return struct.pack(">q", value)


def string_payload(value: str) -> bytes:
    return utf(value)


def int_list(values: list[int]) -> bytes:
    return bytes((3,)) + struct.pack(">i", len(values)) + b"".join(int_payload(value) for value in values)


def compound(entries: list[bytes]) -> bytes:
    return b"".join(entries) + b"\x00"


def compound_list(values: list[bytes]) -> bytes:
    return bytes((10,)) + struct.pack(">i", len(values)) + b"".join(values)


def parse_state(value: str) -> tuple[str, tuple[tuple[str, str], ...]]:
    if "[" not in value:
        return value, ()
    name, raw = value[:-1].split("[", 1)
    properties = tuple(sorted(tuple(part.split("=", 1)) for part in raw.split(",")))
    return name, properties


def palette_entry(state: tuple[str, tuple[tuple[str, str], ...]]) -> bytes:
    name, properties = state
    values = [named(8, "Name", string_payload(name))]
    if properties:
        values.append(named(10, "Properties", compound([
            named(8, key, string_payload(value)) for key, value in properties
        ])))
    return compound(values)


def marker_nbt(metadata: str) -> bytes:
    return compound([
        named(8, "id", string_payload("minecraft:structure_block")),
        named(8, "mode", string_payload("DATA")),
        named(8, "metadata", string_payload(metadata)),
    ])


def block_entry(position: tuple[int, int, int], state_index: int, marker: str | None) -> bytes:
    values = [
        named(9, "pos", int_list(list(position))),
        named(3, "state", int_payload(state_index)),
    ]
    if marker is not None:
        values.append(named(10, "nbt", marker_nbt(marker)))
    return compound(values)


def positions_between(start: list[int], end: list[int]):
    for y in range(start[1], end[1] + 1):
        for z in range(start[2], end[2] + 1):
            for x in range(start[0], end[0] + 1):
                yield x, y, z


def expand(template: dict) -> dict[tuple[int, int, int], tuple[str, str | None]]:
    blocks: dict[tuple[int, int, int], tuple[str, str | None]] = {}
    for operation in template["operations"]:
        op = operation["op"]
        block = operation.get("block", "minecraft:air")
        if op == "set":
            blocks[tuple(operation["pos"])] = (block, operation.get("marker"))
            continue
        start, end = operation["from"], operation["to"]
        for position in positions_between(start, end):
            if op == "hollow":
                x, y, z = position
                if not (x in (start[0], end[0]) or y in (start[1], end[1]) or z in (start[2], end[2])):
                    continue
            blocks[position] = (block, None)
    return blocks


def validate_template(template: dict, seen_ids: set[str]) -> None:
    template_id = template.get("id")
    if not isinstance(template_id, str) or not template_id or template_id in seen_ids:
        raise ValueError(f"invalid or duplicate template id: {template_id!r}")
    seen_ids.add(template_id)
    size = template.get("size")
    if not isinstance(size, list) or len(size) != 3 or any(not isinstance(value, int) or value <= 0 for value in size):
        raise ValueError(f"{template_id}: size must contain three positive integers")
    if size[0] > MAX_HORIZONTAL_SPAN or size[2] > MAX_HORIZONTAL_SPAN:
        raise ValueError(f"{template_id}: horizontal span exceeds {MAX_HORIZONTAL_SPAN} blocks")
    operations = template.get("operations")
    if not isinstance(operations, list) or not operations:
        raise ValueError(f"{template_id}: operations must not be empty")
    loot_markers = 0
    for index, operation in enumerate(operations):
        op = operation.get("op")
        if op not in {"set", "fill", "hollow"}:
            raise ValueError(f"{template_id}: operation {index} has unknown type {op!r}")
        if not isinstance(operation.get("block"), str) or ":" not in operation["block"]:
            raise ValueError(f"{template_id}: operation {index} has an invalid block")
        positions = [operation.get("pos")] if op == "set" else [operation.get("from"), operation.get("to")]
        for position in positions:
            if not isinstance(position, list) or len(position) != 3 or any(not isinstance(value, int) for value in position):
                raise ValueError(f"{template_id}: operation {index} has an invalid position")
            if any(value < 0 or value >= size[axis] for axis, value in enumerate(position)):
                raise ValueError(f"{template_id}: operation {index} lies outside template size")
        if op != "set" and any(operation["from"][axis] > operation["to"][axis] for axis in range(3)):
            raise ValueError(f"{template_id}: operation {index} has inverted bounds")
        marker = operation.get("marker")
        if marker is not None:
            if op != "set" or not operation["block"].startswith("minecraft:structure_block"):
                raise ValueError(f"{template_id}: marker operation {index} must set a structure block")
            loot_markers += marker == "loot"
    if loot_markers != 1:
        raise ValueError(f"{template_id}: expected one loot marker, found {loot_markers}")


def build(template: dict) -> bytes:
    blocks = expand(template)
    palette: list[tuple[str, tuple[tuple[str, str], ...]]] = []
    palette_ids: dict[tuple[str, tuple[tuple[str, str], ...]], int] = {}
    encoded_blocks: list[bytes] = []
    for position in sorted(blocks, key=lambda pos: (pos[1], pos[2], pos[0])):
        raw_state, marker = blocks[position]
        state = parse_state(raw_state)
        if state not in palette_ids:
            palette_ids[state] = len(palette)
            palette.append(state)
        encoded_blocks.append(block_entry(position, palette_ids[state], marker))

    root = compound([
        named(3, "DataVersion", int_payload(DATA_VERSION)),
        named(9, "size", int_list(template["size"])),
        named(9, "palette", compound_list([palette_entry(state) for state in palette])),
        named(9, "blocks", compound_list(encoded_blocks)),
        named(9, "entities", compound_list([])),
    ])
    return named(10, "", root)


def main() -> None:
    templates = json.loads(SOURCE.read_text(encoding="utf-8"))
    if templates.get("format") != 1 or not isinstance(templates.get("templates"), list):
        raise ValueError("landmark template blueprint must use format 1 and contain a templates list")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    expected = set()
    seen_ids: set[str] = set()
    for template in templates["templates"]:
        validate_template(template, seen_ids)
        target = OUTPUT / f"{template['id']}.nbt"
        target.write_bytes(gzip.compress(build(template), compresslevel=9, mtime=0))
        expected.add(target.name)
        print(f"generated {target.relative_to(ROOT)}")
    for stale in OUTPUT.glob("*.nbt"):
        if stale.name not in expected:
            stale.unlink()

    # GameTests below mutate a complete chunk and its east neighbor. A real, mostly-empty
    # structure footprint makes NeoForge's concurrent test grid reserve and clear that space.
    reservation = {
        "size": [50, 8, 34],
        "operations": [{"op": "set", "pos": [0, 0, 0], "block": "minecraft:air"}],
    }
    GAME_TEST_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    GAME_TEST_OUTPUT.write_bytes(gzip.compress(build(reservation), compresslevel=9, mtime=0))
    print(f"generated {GAME_TEST_OUTPUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
