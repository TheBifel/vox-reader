"""Validate the exported quantized speaker classifier before Android packaging."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import onnxruntime as ort
from transformers import AutoTokenizer

from train_speaker_classifier import (
    MAX_LENGTH,
    SEED,
    VALIDATION_NAMES,
    build_examples,
)


def probabilities(logits: np.ndarray) -> np.ndarray:
    shifted = logits - logits.max(axis=1, keepdims=True)
    values = np.exp(shifted)
    return values[:, 1] / values.sum(axis=1)


def score(
    session: ort.InferenceSession,
    tokenizer,
    scenes: list[str],
    candidates: list[str],
) -> np.ndarray:
    encoded = tokenizer(
        scenes,
        [f"Candidate speaker: {candidate}" for candidate in candidates],
        max_length=MAX_LENGTH,
        truncation=True,
        padding="max_length",
        return_tensors="np",
    )
    inputs = {
        "input_ids": encoded["input_ids"].astype(np.int64),
        "attention_mask": encoded["attention_mask"].astype(np.int64),
        "token_type_ids": encoded.get(
            "token_type_ids",
            np.zeros_like(encoded["input_ids"]),
        ).astype(np.int64),
    }
    return probabilities(session.run(["logits"], inputs)[0])


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-dir", type=Path, required=True)
    parser.add_argument(
        "--model-file",
        default="speaker_attribution_quantized.onnx",
    )
    parser.add_argument("--validation-scenes", type=int, default=600)
    args = parser.parse_args()

    tokenizer = AutoTokenizer.from_pretrained(args.model_dir / "checkpoint", use_fast=True)
    session = ort.InferenceSession(
        args.model_dir / args.model_file,
        providers=["CPUExecutionProvider"],
    )
    examples = build_examples(
        SEED + 1,
        VALIDATION_NAMES,
        args.validation_scenes,
    )
    correct = 0
    total = 0
    scored_examples: list[tuple[object, float]] = []
    batch_size = 64
    for start in range(0, len(examples), batch_size):
        batch = examples[start : start + batch_size]
        batch_scores = score(
            session,
            tokenizer,
            [example.scene for example in batch],
            [example.candidate for example in batch],
        )
        predictions = (batch_scores >= 0.5).astype(np.int64)
        labels = np.asarray([example.label for example in batch], dtype=np.int64)
        correct += int((predictions == labels).sum())
        total += len(batch)
        scored_examples.extend(zip(batch, batch_scores.tolist()))

    grouped: dict[str, list[tuple[object, float]]] = {}
    for example, example_score in scored_examples:
        grouped.setdefault(example.scene, []).append((example, example_score))
    ranking_correct = 0
    accepted = 0
    accepted_correct = 0
    for candidates in grouped.values():
        ranked = sorted(candidates, key=lambda item: item[1], reverse=True)
        is_correct = ranked[0][0].label == 1
        ranking_correct += int(is_correct)
        margin = ranked[0][1] - ranked[1][1]
        if ranked[0][1] >= 0.72 and margin >= 0.12:
            accepted += 1
            accepted_correct += int(is_correct)

    probes = (
        (
            "Target dialogue: \u201cApologies, but this is the last of the bandages,\u201d "
            "Lindon said. Yerin watched him.",
            ("Lindon", "Yerin"),
        ),
        (
            "Lindon said, \u201cWe have to move.\u201d "
            "Yerin replied, \u201cNot yet.\u201d Target dialogue: \u201cNow.\u201d",
            ("Lindon", "Yerin"),
        ),
    )
    for scene, candidates in probes:
        scores = score(session, tokenizer, [scene] * len(candidates), list(candidates))
        print("Probe:", ", ".join(
            f"{candidate}={value:.4f}" for candidate, value in zip(candidates, scores)
        ))

    accuracy = correct / max(total, 1)
    print(f"Quantized held-out accuracy={accuracy:.4f} ({correct}/{total})")
    ranking_accuracy = ranking_correct / max(len(grouped), 1)
    accepted_precision = accepted_correct / max(accepted, 1)
    accepted_coverage = accepted / max(len(grouped), 1)
    print(
        f"Candidate ranking accuracy={ranking_accuracy:.4f}; "
        f"accepted precision={accepted_precision:.4f}; "
        f"accepted coverage={accepted_coverage:.4f}"
    )
    metadata_path = args.model_dir / "speaker_attribution.json"
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    metadata["quantizedValidationAccuracy"] = accuracy
    metadata["quantizedRankingAccuracy"] = ranking_accuracy
    metadata["acceptedPrecision"] = accepted_precision
    metadata["acceptedCoverage"] = accepted_coverage
    metadata_path.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
