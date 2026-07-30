"""Export an already trained TinyBERT checkpoint to VoxReader's ONNX assets."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from transformers import AutoModelForSequenceClassification, AutoTokenizer

from train_speaker_classifier import MAX_LENGTH, MODEL_ID, SEED, export_onnx


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-dir", type=Path, required=True)
    parser.add_argument("--training-scenes", type=int, required=True)
    parser.add_argument("--validation-scenes", type=int, required=True)
    parser.add_argument("--validation-accuracy", type=float, required=True)
    args = parser.parse_args()

    checkpoint = args.model_dir / "checkpoint"
    tokenizer = AutoTokenizer.from_pretrained(checkpoint, use_fast=True)
    model = AutoModelForSequenceClassification.from_pretrained(checkpoint)
    quantized_model = export_onnx(model, tokenizer, args.model_dir)

    vocabulary = tokenizer.get_vocab()
    tokens_by_id = [token for token, _ in sorted(vocabulary.items(), key=lambda item: item[1])]
    (args.model_dir / "vocab.txt").write_text(
        "\n".join(tokens_by_id) + "\n",
        encoding="utf-8",
    )
    metadata = {
        "baseModel": MODEL_ID,
        "maxLength": MAX_LENGTH,
        "positiveLabelIndex": 1,
        "recommendedThreshold": 0.72,
        "minimumMargin": 0.12,
        "syntheticTrainingScenes": args.training_scenes,
        "syntheticValidationScenes": args.validation_scenes,
        "validationAccuracy": args.validation_accuracy,
        "seed": SEED,
    }
    (args.model_dir / "speaker_attribution.json").write_text(
        json.dumps(metadata, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Exported {quantized_model} ({quantized_model.stat().st_size / 1_048_576:.1f} MiB)")


if __name__ == "__main__":
    main()
