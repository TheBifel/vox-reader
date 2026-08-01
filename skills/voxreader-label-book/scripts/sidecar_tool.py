#!/usr/bin/env python3
"""Prepare, merge, and validate VoxReader speaker-analysis sidecars."""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import re
import sys
import time
import zipfile
from dataclasses import dataclass, field
from html.parser import HTMLParser
from pathlib import Path, PurePosixPath
from urllib.parse import unquote
import xml.etree.ElementTree as ET


COLUMNS = [
    "phrase_id", "phrase_index", "start", "end", "text_hash", "text",
    "speaker_id", "speaker_name", "voice_group", "voice_slot", "pitch",
    "speech_rate", "confidence", "evidence", "source",
]
IMMUTABLE = COLUMNS[:6]
VOICE_GROUPS = {"NARRATOR", "MALE", "FEMALE", "NEUTRAL"}
EVIDENCE = {
    "USER_LOCKED", "EXPLICIT_TAG", "QUOTE_CONTINUATION", "TURN_PATTERN",
    "CONTEXTUAL", "UNKNOWN",
}
SOURCES = {"USER_LOCKED", "PREANALYSIS_CACHE", "DETERMINISTIC", "TINY_BERT", "UNKNOWN"}
BLOCKS = {"h1", "h2", "h3", "h4", "h5", "h6", "p", "li", "pre"}
SIDECAR_FORMAT = "voxreader-speaker-analysis"
NARRATOR_ID = "narrator"
UNKNOWN_SPEAKER_ID = "unknown"
NARRATOR_PROFILE = ("Narrator", "NARRATOR", "0", "1.0", "0.96")
UNKNOWN_PROFILE = ("Unknown", "NEUTRAL", "0", "1.0", "1.0")
AGE_BANDS = {"CHILD", "YOUNG", "ADULT", "MATURE", "ELDER", "UNKNOWN"}
GENDER_PRESENTATIONS = {"FEMININE", "MASCULINE", "ANDROGYNOUS", "UNKNOWN"}
VOCAL_REGISTERS = {"LOW", "MEDIUM", "HIGH", "UNKNOWN"}
EMOTION_TYPES = {
    "JOY", "SADNESS", "ANGER", "FEAR", "SURPRISE", "DISGUST", "TENSION",
    "EXCITEMENT", "CURIOSITY", "CONFIDENCE", "AFFECTION", "SHAME", "GRIEF",
    "AMUSEMENT", "FRUSTRATION", "RELIEF", "DETERMINATION",
}
SPEECH_REGISTERS = {"FORMAL", "NEUTRAL", "INFORMAL", "MIXED", "UNKNOWN"}
SPEECH_VERBOSITY = {"TERSE", "BALANCED", "VERBOSE", "MIXED", "UNKNOWN"}
SPEECH_TONES = EMOTION_TYPES | {"CALM", "CAUTIOUS", "SARCASTIC", "DRY", "UNKNOWN"}
QUOTATION_MARKS = re.compile(r'["\u201c\u201d\u201e\u00ab\u00bb]')
DIALOGUE_DASH = re.compile(r"^\s*[\u2014\u2013]")
SPEECH_TAG = re.compile(
    r"\b(?:say|says|said|ask|asks|asked|answer|answers|answered|reply|replies|replied|"
    r"whisper|whispers|whispered|shout|shouts|shouted|cry|cries|cried|mutter|muttered|"
    r"remark|remarked|tell|tells|told|\u0441\u043a\u0430\u0437\u0430\u0432|"
    r"\u0441\u043a\u0430\u0437\u0430\u043b\u0430|\u0441\u043a\u0430\u0437\u0430\u043b\u0438|"
    r"\u043a\u0430\u0436\u0435|\u043a\u0430\u0436\u0443\u0442\u044c|"
    r"\u0437\u0430\u043f\u0438\u0442\u0430\u0432|\u0437\u0430\u043f\u0438\u0442\u0430\u043b\u0430|"
    r"\u0437\u0430\u043f\u0438\u0442\u0430\u043b\u0438|\u0432\u0456\u0434\u043f\u043e\u0432\u0456\u0432|"
    r"\u0432\u0456\u0434\u043f\u043e\u0432\u0456\u043b\u0430|\u0432\u0456\u0434\u043f\u043e\u0432\u0456\u043b\u0438|"
    r"\u043f\u0440\u043e\u0448\u0435\u043f\u043e\u0442\u0456\u0432|\u0432\u0438\u0433\u0443\u043a\u043d\u0443\u0432)\b",
    re.IGNORECASE,
)


def validate_character_profile(profile: object, label: str) -> None:
    if not isinstance(profile, dict) or set(profile) != {
        "summary", "speech_style", "evidence_phrase_ids",
    }:
        raise ValueError(f"{label} has an invalid character_profile")
    if not isinstance(profile["summary"], str) or not profile["summary"].strip() or len(profile["summary"]) > 600:
        raise ValueError(f"{label} has an invalid character_profile summary")
    if (not isinstance(profile["evidence_phrase_ids"], list) or
            not profile["evidence_phrase_ids"] or
            not all(isinstance(value, str) and value for value in profile["evidence_phrase_ids"])):
        raise ValueError(f"{label} has invalid character_profile evidence_phrase_ids")
    style = profile["speech_style"]
    if not isinstance(style, dict) or set(style) != {"register", "verbosity", "typical_tones", "markers"}:
        raise ValueError(f"{label} has an invalid speech_style")
    if style["register"] not in SPEECH_REGISTERS or style["verbosity"] not in SPEECH_VERBOSITY:
        raise ValueError(f"{label} has an invalid speech_style value")
    if (not isinstance(style["typical_tones"], list) or not style["typical_tones"] or
            not all(value in SPEECH_TONES for value in style["typical_tones"])):
        raise ValueError(f"{label} has invalid speech_style typical_tones")
    if (not isinstance(style["markers"], list) or not all(isinstance(value, str) and value for value in style["markers"]) or
            len(style["markers"]) > 8):
        raise ValueError(f"{label} has invalid speech_style markers")


def validate_character_profiles(characters: object) -> None:
    """Validate optional global character descriptors without interpreting them as facts."""
    if not isinstance(characters, list):
        raise ValueError("characters must be an array when present")
    seen_ids: set[str] = set()
    required = {
        "speaker_id", "speaker_name", "aliases", "voice_group", "voice_slot",
        "pitch", "speech_rate",
    }
    allowed = required | {"traits", "character_profile"}
    for number, character in enumerate(characters, 1):
        if not isinstance(character, dict):
            raise ValueError(f"character {number} must be an object")
        missing = required - set(character)
        extra = set(character) - allowed
        if missing or extra:
            field_name = sorted(missing or extra)[0]
            raise ValueError(f"character {number} has invalid field {field_name!r}")
        identifier = character["speaker_id"]
        if not isinstance(identifier, str) or not identifier or identifier in seen_ids:
            raise ValueError(f"character {number} has an invalid or duplicate speaker_id")
        if identifier in {NARRATOR_ID, UNKNOWN_SPEAKER_ID}:
            raise ValueError(f"character {number} cannot redefine a system speaker")
        seen_ids.add(identifier)
        if (not isinstance(character["speaker_name"], str) or
                not isinstance(character["aliases"], list) or
                not all(isinstance(alias, str) for alias in character["aliases"]) or
                character["voice_group"] not in VOICE_GROUPS):
            raise ValueError(f"character {number} has an invalid identity or voice profile")
        if (not isinstance(character["voice_slot"], int) or isinstance(character["voice_slot"], bool) or
                not isinstance(character["pitch"], (int, float)) or
                not isinstance(character["speech_rate"], (int, float))):
            raise ValueError(f"character {number} has invalid numeric voice fields")
        traits = character.get("traits")
        if traits is not None:
            if not isinstance(traits, dict) or set(traits) != {
                "age_band", "gender_presentation", "vocal_register",
            }:
                raise ValueError(f"character {number} has invalid traits")
            if (traits["age_band"] not in AGE_BANDS or
                    traits["gender_presentation"] not in GENDER_PRESENTATIONS or
                    traits["vocal_register"] not in VOCAL_REGISTERS):
                raise ValueError(f"character {number} has an invalid trait value")
        profile = character.get("character_profile")
        if profile is not None:
            validate_character_profile(profile, f"character {number}")


def normalize_emotion(value: object, label: str) -> str:
    if not isinstance(value, dict) or set(value) != {"type", "intensity", "confidence"}:
        raise ValueError(f"{label}: emotion must contain type, intensity, and confidence")
    if value["type"] not in EMOTION_TYPES:
        raise ValueError(f"{label}: invalid emotion type")
    if (not isinstance(value["intensity"], (int, float)) or isinstance(value["intensity"], bool) or
            not isinstance(value["confidence"], (int, float)) or isinstance(value["confidence"], bool)):
        raise ValueError(f"{label}: emotion intensity and confidence must be numbers")
    if not 0.0 <= float(value["intensity"]) <= 1.0 or not 0.85 <= float(value["confidence"]) <= 1.0:
        raise ValueError(f"{label}: emotion values outside permitted range")
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def read_emotion(value: object, label: str) -> str:
    if isinstance(value, str):
        try:
            value = json.loads(value)
        except json.JSONDecodeError as error:
            raise ValueError(f"{label}: invalid emotion JSON") from error
    return normalize_emotion(value, label)


def validate_system_speaker(row: dict[str, str], label: str) -> str | None:
    profile = tuple(row[key] for key in (
        "speaker_name", "voice_group", "voice_slot", "pitch", "speech_rate",
    ))
    if row["speaker_id"] == NARRATOR_ID and profile != NARRATOR_PROFILE:
        return f"{label}: narrator must use the fixed Narrator system profile"
    if row["speaker_id"] == UNKNOWN_SPEAKER_ID:
        if profile != UNKNOWN_PROFILE:
            return f"{label}: unknown must use the fixed Unknown system profile"
        if row["confidence"] != "0.0" or row["evidence"] != "UNKNOWN" or row["source"] != "UNKNOWN":
            return f"{label}: unknown must have confidence 0.0, UNKNOWN evidence, and UNKNOWN source"
    return None


def legacy_escape(value: object) -> str:
    return (str(value).replace("\\", "\\\\").replace("\t", "\\t")
            .replace("\n", "\\n").replace("\r", "\\r"))


def legacy_unescape(value: str) -> str:
    out, index = [], 0
    mapping = {"t": "\t", "n": "\n", "r": "\r", "\\": "\\"}
    while index < len(value):
        if value[index] != "\\":
            out.append(value[index])
            index += 1
            continue
        if index + 1 >= len(value) or value[index + 1] not in mapping:
            raise ValueError("Invalid TSV escape sequence")
        out.append(mapping[value[index + 1]])
        index += 2
    return "".join(out)


@dataclass
class Sidecar:
    metadata: dict[str, str]
    rows: list[dict[str, str]]
    characters: list[dict[str, object]] = field(default_factory=list)


def read_sidecar(path: Path) -> Sidecar:
    content = path.read_text(encoding="utf-8-sig")
    if content.lstrip().startswith("{"):
        return read_json_sidecar(content)
    return read_legacy_tsv(content)


def read_json_sidecar(content: str) -> Sidecar:
    document = json.loads(content)
    if not isinstance(document, dict):
        raise ValueError("The sidecar JSON root must be an object")
    if document.get("format") != SIDECAR_FORMAT or document.get("schema_version") != 1:
        raise ValueError("Not a VoxReader speaker-analysis JSON v1 file")
    phrases = document.get("phrases")
    if not isinstance(phrases, list):
        raise ValueError("The sidecar JSON must contain a phrases array")
    metadata_keys = [
        "format", "schema_version", "book_id", "provider_id", "created_at",
        "manifest_version", "segmentation_version", "attribution_policy_version",
        "provider_schema_version",
    ]
    extra_metadata = set(document) - set(metadata_keys) - {"phrases", "characters"}
    if extra_metadata:
        raise ValueError(f"Unknown JSON metadata {sorted(extra_metadata)[0]!r}")
    metadata = {}
    for key in metadata_keys:
        if key not in document:
            raise ValueError(f"Missing JSON metadata {key!r}")
        expected_string = key in {"format", "book_id", "provider_id"}
        value = document[key]
        if expected_string and not isinstance(value, str):
            raise ValueError(f"JSON metadata {key!r} must be a string")
        if not expected_string and (not isinstance(value, int) or isinstance(value, bool)):
            raise ValueError(f"JSON metadata {key!r} must be an integer")
        metadata[key] = str(document[key])
    rows = []
    for number, phrase in enumerate(phrases, 1):
        if not isinstance(phrase, dict):
            raise ValueError(f"Phrase {number} must be a JSON object")
        missing = [column for column in COLUMNS if column not in phrase]
        if missing:
            raise ValueError(f"Phrase {number} is missing {missing[0]!r}")
        extra = set(phrase) - set(COLUMNS) - {"emotion"}
        if extra:
            raise ValueError(f"Phrase {number} has unknown field {sorted(extra)[0]!r}")
        for key in {"phrase_index", "start", "end", "voice_slot"}:
            if not isinstance(phrase[key], int) or isinstance(phrase[key], bool):
                raise ValueError(f"Phrase {number} field {key!r} must be an integer")
        for key in {"pitch", "speech_rate", "confidence"}:
            if not isinstance(phrase[key], (int, float)) or isinstance(phrase[key], bool):
                raise ValueError(f"Phrase {number} field {key!r} must be a number")
        for key in set(COLUMNS) - {
            "phrase_index", "start", "end", "voice_slot", "pitch", "speech_rate", "confidence"
        }:
            if not isinstance(phrase[key], str):
                raise ValueError(f"Phrase {number} field {key!r} must be a string")
        row = {column: str(phrase[column]) for column in COLUMNS}
        if "emotion" in phrase:
            row["emotion"] = normalize_emotion(phrase["emotion"], f"Phrase {number}")
        rows.append(row)
    characters = document.get("characters", [])
    validate_character_profiles(characters)
    return Sidecar(metadata, rows, characters)


def read_legacy_tsv(content: str) -> Sidecar:
    """Read old v1 TSV only to migrate existing local analysis runs to JSON."""
    metadata: dict[str, str] = {}
    data: list[str] = []
    for raw in content.splitlines():
        if not raw.strip():
            continue
        if raw.startswith("# "):
            key, separator, value = raw[2:].partition("=")
            if not separator:
                raise ValueError(f"Invalid metadata line: {raw}")
            metadata[key] = legacy_unescape(value)
        else:
            data.append(raw)
    if metadata.get("voxreader_speaker_analysis_tsv") != "1":
        raise ValueError("Not a VoxReader speaker-analysis TSV v1")
    if not data or data[0].split("\t") != COLUMNS:
        raise ValueError("Invalid TSV header")
    rows = []
    for number, line in enumerate(data[1:], 1):
        cells = line.split("\t")
        if len(cells) != len(COLUMNS):
            raise ValueError(f"Row {number} has {len(cells)} cells; expected {len(COLUMNS)}")
        rows.append(dict(zip(COLUMNS, map(legacy_unescape, cells))))
    metadata["format"] = SIDECAR_FORMAT
    metadata["schema_version"] = metadata.pop("voxreader_speaker_analysis_tsv")
    return Sidecar(metadata, rows)


def write_sidecar(path: Path, sidecar: Sidecar) -> None:
    metadata_order = [
        "format", "schema_version", "book_id", "provider_id", "created_at",
        "manifest_version", "segmentation_version", "attribution_policy_version",
        "provider_schema_version",
    ]
    integer_metadata = {
        "schema_version", "created_at", "manifest_version",
        "segmentation_version", "attribution_policy_version",
        "provider_schema_version",
    }
    document = {
        key: int(sidecar.metadata[key]) if key in integer_metadata else sidecar.metadata[key]
        for key in metadata_order
    }
    if sidecar.characters:
        document["characters"] = sidecar.characters
    document["phrases"] = [
        ({
            key: (
                int(value) if key in {"phrase_index", "start", "end", "voice_slot"}
                else float(value) if key in {"pitch", "speech_rate", "confidence"}
                else value
            )
            for key, value in row.items() if key != "emotion"
        } | ({"emotion": json.loads(row["emotion"])} if "emotion" in row else {}))
        for row in sidecar.rows
    ]
    path.write_text(
        json.dumps(document, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def validate(sidecar: Sidecar, base: Sidecar | None = None) -> list[str]:
    errors: list[str] = []
    required_meta = {
        "format": SIDECAR_FORMAT, "schema_version": "1", "manifest_version": "1",
        "segmentation_version": "2", "attribution_policy_version": "1",
        "provider_schema_version": "1",
    }
    for key, expected in required_meta.items():
        if sidecar.metadata.get(key) != expected:
            errors.append(f"metadata {key!r} must be {expected!r}")
    for key in ("book_id", "provider_id", "created_at"):
        if not sidecar.metadata.get(key):
            errors.append(f"metadata {key!r} is missing")
    try:
        validate_character_profiles(sidecar.characters)
    except ValueError as error:
        errors.append(str(error))

    seen_ids, seen_indexes = set(), set()
    definitions: dict[str, tuple[str, str, str, str, str]] = {}
    for number, row in enumerate(sidecar.rows, 1):
        label = f"row {number}"
        if row["phrase_id"] in seen_ids:
            errors.append(f"{label}: duplicate phrase_id")
        seen_ids.add(row["phrase_id"])
        if row["phrase_index"] in seen_indexes:
            errors.append(f"{label}: duplicate phrase_index")
        seen_indexes.add(row["phrase_index"])
        if row["voice_group"] not in VOICE_GROUPS:
            errors.append(f"{label}: invalid voice_group")
        if row["evidence"] not in EVIDENCE:
            errors.append(f"{label}: invalid evidence")
        if row["source"] not in SOURCES:
            errors.append(f"{label}: invalid source")
        system_error = validate_system_speaker(row, label)
        if system_error:
            errors.append(system_error)
        try:
            pitch, rate, confidence = map(float, (row["pitch"], row["speech_rate"], row["confidence"]))
            int(row["voice_slot"])
            if not 0.5 <= pitch <= 1.6:
                errors.append(f"{label}: pitch outside 0.5..1.6")
            if not 0.5 <= rate <= 1.5:
                errors.append(f"{label}: speech_rate outside 0.5..1.5")
            if not 0.0 <= confidence <= 1.0:
                errors.append(f"{label}: confidence outside 0..1")
        except ValueError:
            errors.append(f"{label}: invalid numeric value")
        if not row["speaker_id"] or len(row["speaker_id"]) > 128:
            errors.append(f"{label}: invalid speaker_id")
        if not row["speaker_name"] or len(row["speaker_name"]) > 160:
            errors.append(f"{label}: invalid speaker_name")
        if row["speaker_id"] != UNKNOWN_SPEAKER_ID:
            definition = tuple(row[key] for key in (
                "speaker_name", "voice_group", "voice_slot", "pitch", "speech_rate"
            ))
            previous = definitions.setdefault(row["speaker_id"], definition)
            if previous != definition:
                errors.append(f"{label}: inconsistent profile for {row['speaker_id']!r}")
        if "emotion" in row:
            try:
                read_emotion(row["emotion"], label)
            except ValueError as error:
                errors.append(str(error))

    for character in sidecar.characters:
        definition = definitions.get(character["speaker_id"])
        if definition is None:
            continue
        expected = tuple(str(character[key]) for key in (
            "speaker_name", "voice_group", "voice_slot", "pitch", "speech_rate"
        ))
        if definition != expected:
            errors.append(f"character profile for {character['speaker_id']!r} differs from phrase rows")

    if base:
        if sidecar.metadata.get("book_id") != base.metadata.get("book_id"):
            errors.append("book_id differs from base")
        if len(sidecar.rows) != len(base.rows):
            errors.append("row count differs from base")
        base_by_id = {row["phrase_id"]: row for row in base.rows}
        for number, row in enumerate(sidecar.rows, 1):
            expected = base_by_id.get(row["phrase_id"])
            if expected is None:
                errors.append(f"row {number}: phrase_id not present in base")
                continue
            for field in IMMUTABLE:
                if row[field] != expected[field]:
                    errors.append(f"row {number}: immutable field {field!r} differs from base")
    return list(dict.fromkeys(errors))


class BlockParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.stack: list[str] = []
        self.current: list[str] | None = None
        self.blocks: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        tag = tag.lower()
        self.stack.append(tag)
        if tag in BLOCKS and self.current is None:
            self.current = []

    def handle_endtag(self, tag: str) -> None:
        tag = tag.lower()
        if tag in BLOCKS and self.current is not None:
            value = re.sub(r"\s+", " ", "".join(self.current)).strip()
            if value:
                self.blocks.append(value)
            self.current = None
        if tag in self.stack:
            reverse_index = self.stack[::-1].index(tag)
            del self.stack[len(self.stack) - reverse_index - 1:]

    def handle_data(self, data: str) -> None:
        if self.current is not None and not any(tag in {"script", "style", "nav", "svg"} for tag in self.stack):
            self.current.append(data)


def epub_text(path: Path) -> tuple[str, str]:
    book_id = hashlib.sha256(path.read_bytes()).hexdigest()
    with zipfile.ZipFile(path) as archive:
        container = ET.fromstring(archive.read("META-INF/container.xml"))
        rootfile = next(node for node in container.iter() if node.tag.endswith("rootfile"))
        package_path = rootfile.attrib["full-path"]
        package = ET.fromstring(archive.read(package_path))
        manifest = {
            node.attrib["id"]: node.attrib
            for node in package.iter() if node.tag.endswith("item") and "id" in node.attrib
        }
        spine = [
            node.attrib["idref"] for node in package.iter()
            if node.tag.endswith("itemref") and "idref" in node.attrib
        ]
        package_dir = PurePosixPath(package_path).parent
        chapters = []
        for item_id in spine or list(manifest):
            item = manifest.get(item_id, {})
            href = unquote(item.get("href", "").split("#", 1)[0])
            media = item.get("media-type", "")
            if not (media in {"application/xhtml+xml", "text/html"} or href.lower().endswith((".xhtml", ".html", ".htm"))):
                continue
            entry = str((package_dir / href))
            parser = BlockParser()
            parser.feed(archive.read(entry).decode("utf-8"))
            if parser.blocks:
                chapters.append("\n\n".join(parser.blocks))
        text = "\n\n❦\n\n".join(chapters)
        text = re.sub(r"[ \t]+\n", "\n", text)
        text = re.sub(r"\n{3,}", "\n\n", text).strip()
        if not text:
            raise ValueError("No readable text found in EPUB")
        return book_id, html.unescape(text)


def provisional_phrases(text: str) -> list[tuple[int, int, str]]:
    """Approximate the app segmenter; app-exported JSON remains authoritative."""
    boundaries = [0]
    for match in re.finditer(r"(?<=[.!?])(?:[”\"»])?(?=\s+)|\n\n+", text):
        boundaries.append(match.end())
    boundaries.append(len(text))
    raw: list[tuple[int, int]] = []
    for start, end in zip(boundaries, boundaries[1:]):
        while start < end and text[start].isspace():
            start += 1
        while end > start and text[end - 1].isspace():
            end -= 1
        if start < end:
            raw.append((start, end))

    result: list[tuple[int, int, str]] = []
    quote_chars = {'"', "“", "”", "„", "«", "»"}
    for start, end in raw:
        splits = [start]
        in_dialogue = False
        for index in range(start, end):
            char = text[index]
            if char not in quote_chars:
                continue
            if not in_dialogue:
                if index > splits[-1]:
                    splits.append(index)
                in_dialogue = True
            else:
                splits.append(index + 1)
                in_dialogue = False
        splits.append(end)
        for left, right in zip(splits, splits[1:]):
            while left < right and text[left].isspace():
                left += 1
            while right > left and text[right - 1].isspace():
                right -= 1
            if left < right:
                result.append((left, right, text[left:right]))
    return result


def normalized_hash(text: str) -> str:
    normalized = re.sub(r"\s+", " ", text.lower()).strip()
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def template_from_epub(path: Path) -> Sidecar:
    book_id, text = epub_text(path)
    rows = []
    for index, (start, end, phrase) in enumerate(provisional_phrases(text)):
        text_hash = normalized_hash(phrase)
        phrase_id = hashlib.sha256(
            f"{book_id}|1|2|{start}|{end}|{text_hash}".encode("utf-8")
        ).hexdigest()
        rows.append({
            "phrase_id": phrase_id, "phrase_index": str(index), "start": str(start),
            "end": str(end), "text_hash": text_hash, "text": phrase,
            "speaker_id": UNKNOWN_SPEAKER_ID, "speaker_name": "Unknown",
            "voice_group": "NEUTRAL", "voice_slot": "0", "pitch": "1.0",
            "speech_rate": "1.0", "confidence": "0.0", "evidence": "UNKNOWN",
            "source": "UNKNOWN",
        })
    return Sidecar({
        "format": SIDECAR_FORMAT, "schema_version": "1", "book_id": book_id,
        "provider_id": "provisional-epub-template", "created_at": str(int(time.time() * 1000)),
        "manifest_version": "1", "segmentation_version": "2",
        "attribution_policy_version": "1", "provider_schema_version": "1",
    }, rows)


def command_prepare(args: argparse.Namespace) -> None:
    source = Path(args.input)
    work = Path(args.out_dir)
    work.mkdir(parents=True, exist_ok=True)
    if source.suffix.lower() == ".epub":
        sidecar = template_from_epub(source)
    else:
        sidecar = read_sidecar(source)
    errors = validate(sidecar)
    if errors:
        raise ValueError("\n".join(errors))
    base = work / "base.json"
    write_sidecar(base, sidecar)
    packet_dir = work / "packets"
    packet_dir.mkdir(exist_ok=True)
    if args.chunk_size < 0:
        raise ValueError("chunk-size must be zero (whole chapter) or a positive phrase count")
    chapters = chapter_ranges(sidecar.rows) if args.by_chapter else [(0, len(sidecar.rows))]
    catalog = []
    packet_count = 0
    for chapter_index, (chapter_start, chapter_end) in enumerate(chapters):
        chapter_packets = []
        size = args.chunk_size or (chapter_end - chapter_start)
        for offset in range(chapter_start, chapter_end, size):
            owned_end = min(chapter_end, offset + size)
            rows = sidecar.rows[offset:owned_end]
            before = sidecar.rows[max(0, offset - args.context):offset]
            after = sidecar.rows[owned_end:min(len(sidecar.rows), owned_end + args.context)]
            packet = {
                "contract": "voxreader-attribution-decisions-v1",
                "book_id": sidecar.metadata["book_id"],
                "chapter_index": chapter_index,
                "part_index": len(chapter_packets),
                "phrase_range": [offset, owned_end - 1],
                "context_before": [
                    {key: row[key] for key in ("phrase_id", "phrase_index", "text")}
                    for row in before
                ],
                "phrases": [
                    {key: row[key] for key in ("phrase_id", "phrase_index", "text")}
                    for row in rows
                ],
                "context_after": [
                    {key: row[key] for key in ("phrase_id", "phrase_index", "text")}
                    for row in after
                ],
            }
            filename = f"chapter-{chapter_index:04d}-part-{len(chapter_packets):04d}.json"
            (packet_dir / filename).write_text(
                json.dumps(packet, ensure_ascii=False, indent=2), encoding="utf-8"
            )
            chapter_packets.append(filename)
            packet_count += 1
        catalog.append({
            "chapter_index": chapter_index,
            "phrase_range": [chapter_start, chapter_end - 1],
            "packets": chapter_packets,
        })
    (work / "chapter-catalog.json").write_text(
        json.dumps(catalog, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (work / "registry.json").write_text(
        json.dumps({"version": 1, "characters": []}, indent=2) + "\n", encoding="utf-8"
    )
    (work / "decisions").mkdir(exist_ok=True)
    print(f"Prepared {len(sidecar.rows)} phrases in {packet_count} packets across {len(chapters)} chapters")
    print(f"Base: {base}")


def chapter_ranges(rows: list[dict[str, str]]) -> list[tuple[int, int]]:
    """Derive app chapter ranges from the EPUB separator retained in phrase text."""
    starts = [0]
    for index, row in enumerate(rows):
        # SentenceSegmenter can retain the separator and next heading/prose in one phrase,
        # so that entire phrase belongs to the chapter beginning at the separator.
        if "❦" in row["text"] and index > 0:
            starts.append(index)
    starts = sorted(set(starts))
    ranges = [(start, end) for start, end in zip(starts, starts[1:] + [len(rows)]) if start < end]
    return ranges or [(0, len(rows))]


def decision_files(path: Path) -> list[Path]:
    if path.is_file():
        return [path]
    if not path.is_dir():
        raise ValueError(f"Decision path does not exist: {path}")
    files = sorted(path.glob("*.jsonl"))
    if not files:
        raise ValueError(f"No .jsonl decision files found in {path}")
    return files


def read_registry(path: Path) -> list[dict[str, object]]:
    document = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(document, dict) or document.get("version") != 1 or set(document) != {"version", "characters"}:
        raise ValueError("Registry must be a version 1 object containing only characters")
    characters = document["characters"]
    validate_character_profiles(characters)
    return characters


def command_merge(args: argparse.Namespace) -> None:
    base = read_sidecar(Path(args.base))
    decisions: dict[str, dict[str, object]] = {}
    for decision_file in decision_files(Path(args.decisions)):
        with decision_file.open(encoding="utf-8-sig") as stream:
            for number, line in enumerate(stream, 1):
                if not line.strip():
                    continue
                decision = json.loads(line)
                phrase_id = str(decision.get("phrase_id", ""))
                if phrase_id in decisions:
                    raise ValueError(
                        f"Duplicate decision for phrase_id {phrase_id!r} "
                        f"in {decision_file.name}:{number}"
                    )
                decisions[phrase_id] = decision
    known = {row["phrase_id"] for row in base.rows}
    extra = set(decisions) - known
    if extra:
        raise ValueError(f"Decisions contain {len(extra)} unknown phrase IDs")
    if not args.allow_partial and len(decisions) != len(base.rows):
        raise ValueError(f"Expected {len(base.rows)} decisions; received {len(decisions)}")

    rows = []
    for row in base.rows:
        decision = decisions.get(row["phrase_id"])
        if decision is None:
            rows.append(row.copy())
            continue
        updated = row.copy()
        for key in (
            "speaker_id", "speaker_name", "voice_group", "voice_slot", "pitch",
            "speech_rate", "confidence", "evidence",
        ):
            if key not in decision:
                raise ValueError(f"Decision {row['phrase_id']} is missing {key!r}")
            updated[key] = str(decision[key])
        if "emotion" in decision:
            updated["emotion"] = normalize_emotion(decision["emotion"], f"Decision {row['phrase_id']}")
        else:
            updated.pop("emotion", None)
        try:
            updated["voice_slot"] = str(int(updated["voice_slot"]))
            for key in ("pitch", "speech_rate", "confidence"):
                updated[key] = str(float(updated[key]))
        except ValueError as error:
            raise ValueError(f"Decision {row['phrase_id']} has an invalid numeric value") from error
        updated["source"] = "UNKNOWN" if updated["speaker_id"] == UNKNOWN_SPEAKER_ID else "PREANALYSIS_CACHE"
        rows.append(updated)
    metadata = base.metadata.copy()
    metadata["provider_id"] = args.provider_id
    metadata["created_at"] = str(int(time.time() * 1000))
    characters = read_registry(Path(args.registry)) if args.registry else base.characters
    result = Sidecar(metadata, rows, characters)
    errors = validate(result, base)
    if errors:
        raise ValueError("\n".join(errors[:20]))
    write_sidecar(Path(args.output), result)
    unknown = sum(row["speaker_id"] == UNKNOWN_SPEAKER_ID for row in rows)
    characters = {row["speaker_id"] for row in rows} - {UNKNOWN_SPEAKER_ID, NARRATOR_ID}
    print(f"Wrote {args.output}: {len(rows) - unknown}/{len(rows)} attributed, {unknown} unknown, {len(characters)} characters")


def command_validate(args: argparse.Namespace) -> None:
    sidecar = read_sidecar(Path(args.input))
    base = read_sidecar(Path(args.base)) if args.base else None
    errors = validate(sidecar, base)
    if errors:
        print("\n".join(f"ERROR: {error}" for error in errors), file=sys.stderr)
        raise SystemExit(1)
    unknown = sum(row["speaker_id"] == UNKNOWN_SPEAKER_ID for row in sidecar.rows)
    characters = {row["speaker_id"] for row in sidecar.rows} - {UNKNOWN_SPEAKER_ID, NARRATOR_ID}
    print(f"VALID: {len(sidecar.rows)} rows, {unknown} unknown, {len(characters)} characters")


def command_status(args: argparse.Namespace) -> None:
    work = Path(args.work_dir)
    base_path = work / "base.json"
    if not base_path.exists():
        base_path = work / "base.tsv"
    base = read_sidecar(base_path)
    catalog = json.loads((work / "chapter-catalog.json").read_text(encoding="utf-8"))
    completed: set[str] = set()
    invalid_files = 0
    decisions_path = work / "decisions"
    if decisions_path.exists():
        for path in sorted(decisions_path.glob("*.jsonl")):
            try:
                with path.open(encoding="utf-8-sig") as stream:
                    for line in stream:
                        if line.strip():
                            phrase_id = str(json.loads(line).get("phrase_id", ""))
                            if phrase_id:
                                completed.add(phrase_id)
            except (OSError, json.JSONDecodeError):
                invalid_files += 1
    rows_by_index = {int(row["phrase_index"]): row for row in base.rows}
    total_completed = 0
    for chapter in catalog:
        start, end = chapter["phrase_range"]
        phrase_ids = [
            rows_by_index[index]["phrase_id"]
            for index in range(start, end + 1)
            if index in rows_by_index
        ]
        count = sum(phrase_id in completed for phrase_id in phrase_ids)
        total_completed += count
        state = "complete" if count == len(phrase_ids) else "pending"
        print(
            f"chapter {chapter['chapter_index']}: {state} "
            f"({count}/{len(phrase_ids)} decisions)"
        )
    print(
        f"TOTAL: {total_completed}/{len(base.rows)} decisions; "
        f"{invalid_files} unreadable decision files"
    )


def dialogue_candidate(row: dict[str, str], before: dict[str, str] | None, after: dict[str, str] | None) -> bool:
    """Find likely direct speech without assuming that every book uses quotation marks."""
    text = row["text"]
    return bool(
        QUOTATION_MARKS.search(text) or DIALOGUE_DASH.search(text) or SPEECH_TAG.search(text) or
        (before and SPEECH_TAG.search(before["text"])) or
        (after and SPEECH_TAG.search(after["text"]))
    )


def command_audit(args: argparse.Namespace) -> None:
    """Report conservative unknown-dialogue quality gates for coordinator retry decisions."""
    sidecar = read_sidecar(Path(args.input))
    errors = validate(sidecar)
    if errors:
        raise ValueError("\n".join(errors[:20]))
    failed = []
    totals = {"rows": 0, "unknown": 0, "dialogue": 0, "dialogue_unknown": 0}
    for chapter_index, (start, end) in enumerate(chapter_ranges(sidecar.rows)):
        rows = sidecar.rows[start:end]
        unknown = sum(row["speaker_id"] == UNKNOWN_SPEAKER_ID for row in rows)
        dialogue_rows = [
            row for offset, row in enumerate(rows)
            if dialogue_candidate(row, rows[offset - 1] if offset else None,
                                  rows[offset + 1] if offset + 1 < len(rows) else None)
        ]
        dialogue_unknown = sum(row["speaker_id"] == UNKNOWN_SPEAKER_ID for row in dialogue_rows)
        rate = dialogue_unknown / len(dialogue_rows) if dialogue_rows else 0.0
        totals["rows"] += len(rows)
        totals["unknown"] += unknown
        totals["dialogue"] += len(dialogue_rows)
        totals["dialogue_unknown"] += dialogue_unknown
        requires_repair = dialogue_unknown > args.max_unknown_dialogue or rate > args.max_unknown_dialogue_rate
        if requires_repair:
            failed.append(chapter_index)
        print(
            f"chapter {chapter_index}: {unknown}/{len(rows)} unknown; "
            f"dialogue-candidate heuristic {dialogue_unknown}/{len(dialogue_rows)} ({rate:.1%}); "
            f"{'REPAIR' if requires_repair else 'ok'}"
        )
    total_rate = totals["dialogue_unknown"] / totals["dialogue"] if totals["dialogue"] else 0.0
    print(
        f"TOTAL: {totals['unknown']}/{totals['rows']} unknown; dialogue-candidate heuristic "
        f"{totals['dialogue_unknown']}/{totals['dialogue']} ({total_rate:.1%}); "
        f"repair chapters={failed}"
    )
    if failed:
        raise SystemExit(2)


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    commands = result.add_subparsers(dest="command", required=True)
    prepare = commands.add_parser("prepare")
    prepare.add_argument("input")
    prepare.add_argument("--out-dir", required=True)
    prepare.add_argument("--chunk-size", type=int, default=0,
                         help="Maximum phrases per packet; 0 (default) keeps each chapter intact")
    prepare.add_argument("--context", type=int, default=8)
    prepare.add_argument("--by-chapter", action="store_true")
    prepare.set_defaults(func=command_prepare)
    merge = commands.add_parser("merge")
    merge.add_argument("base")
    merge.add_argument("decisions")
    merge.add_argument("--output", required=True)
    merge.add_argument("--provider-id", required=True)
    merge.add_argument("--registry", help="Validated registry.json to embed in the final sidecar")
    merge.add_argument("--allow-partial", action="store_true")
    merge.set_defaults(func=command_merge)
    check = commands.add_parser("validate")
    check.add_argument("input")
    check.add_argument("--base")
    check.set_defaults(func=command_validate)
    status = commands.add_parser("status")
    status.add_argument("work_dir")
    status.set_defaults(func=command_status)
    audit = commands.add_parser("audit")
    audit.add_argument("input")
    audit.add_argument("--max-unknown-dialogue", type=int, default=2)
    audit.add_argument("--max-unknown-dialogue-rate", type=float, default=0.02)
    audit.set_defaults(func=command_audit)
    return result


def main() -> None:
    args = parser().parse_args()
    try:
        args.func(args)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile, ET.ParseError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
