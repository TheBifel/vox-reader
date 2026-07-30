"""Fine-tune and export VoxReader's on-device TinyBERT speaker scorer.

The training data is deliberately synthetic and contains no book text.  Each
scene is converted into two candidate pairs: one correct speaker and one
incorrect speaker.  The Android app applies deterministic dialogue rules first
and uses this model only for ambiguous candidate selection.
"""

from __future__ import annotations

import argparse
import json
import math
import random
import shutil
import time
from dataclasses import dataclass
from pathlib import Path

import torch
from onnxruntime.quantization import QuantType, quantize_dynamic
from torch.optim import AdamW
from torch.utils.data import DataLoader, Dataset
from transformers import AutoModelForSequenceClassification, AutoTokenizer
from transformers.optimization import get_linear_schedule_with_warmup


MODEL_ID = "huawei-noah/TinyBERT_General_4L_312D"
MAX_LENGTH = 160
SEED = 734_921

TRAIN_NAMES = (
    "Alice", "Arthur", "Beatrice", "Bren", "Clara", "Corin", "Diana", "Dorian",
    "Elena", "Elias", "Fiona", "Finn", "Grace", "Gareth", "Helena", "Henry",
    "Iris", "Ivan", "Julia", "Julian", "Kara", "Kael", "Lena", "Leon",
    "Mara", "Marcus", "Nora", "Nolan", "Olivia", "Orin", "Petra", "Peter",
    "Quinn", "Rhea", "Rowan", "Sara", "Silas", "Talia", "Thomas", "Una",
    "Victor", "Vera", "Willow", "William", "Xara", "Xavier", "Yara", "Zane",
)
VALIDATION_NAMES = (
    "Lindon", "Yerin", "Eithan", "Mercy", "Cassian", "Elara", "Milo", "Sable",
    "Tarin", "Violet", "Wren", "Zora",
)
SPEECH_VERBS = (
    "said", "asked", "replied", "answered", "whispered", "murmured",
    "shouted", "called", "added", "continued", "remarked",
)
UTTERANCES = (
    "We should leave before sunrise",
    "I never agreed to this plan",
    "There is another path through the valley",
    "Wait here until I return",
    "That was not what I expected",
    "You heard the warning as clearly as I did",
    "I can explain everything",
    "Do not open that door",
    "The map ends at the old bridge",
    "We have only a few minutes",
    "It will be safer if we stay together",
    "Tell me what happened",
    "I thought you had already gone",
    "This belongs to you",
    "No one else needs to know",
    "Are you certain about that",
)
ACTIONS = (
    "folded their arms",
    "looked toward the window",
    "stepped away from the fire",
    "lowered their voice",
    "closed the book",
    "shook their head",
    "watched the empty road",
    "placed the cup on the table",
)
QUOTE_PAIRS = (("\"", "\""), ("\u201c", "\u201d"), ("\u00ab", "\u00bb"))


@dataclass(frozen=True)
class PairExample:
    scene: str
    candidate: str
    label: int
    scenario: str


class EncodedPairs(Dataset):
    def __init__(self, tokenizer, examples: list[PairExample]) -> None:
        encoded = tokenizer(
            [example.scene for example in examples],
            [f"Candidate speaker: {example.candidate}" for example in examples],
            max_length=MAX_LENGTH,
            truncation=True,
            padding="max_length",
            return_tensors="pt",
        )
        self.input_ids = encoded["input_ids"]
        self.attention_mask = encoded["attention_mask"]
        self.token_type_ids = encoded.get(
            "token_type_ids",
            torch.zeros_like(self.input_ids),
        )
        self.labels = torch.tensor([example.label for example in examples], dtype=torch.long)

    def __len__(self) -> int:
        return len(self.labels)

    def __getitem__(self, index: int) -> dict[str, torch.Tensor]:
        return {
            "input_ids": self.input_ids[index],
            "attention_mask": self.attention_mask[index],
            "token_type_ids": self.token_type_ids[index],
            "labels": self.labels[index],
        }


def quoted(rng: random.Random, text: str) -> str:
    opening, closing = rng.choice(QUOTE_PAIRS)
    return f"{opening}{text}{closing}"


def distinct_names(rng: random.Random, names: tuple[str, ...], count: int = 3) -> list[str]:
    return rng.sample(names, count)


def create_scene(
    rng: random.Random,
    names: tuple[str, ...],
    scenario: str,
) -> tuple[str, str, str]:
    speaker, other, third = distinct_names(rng, names)
    target = rng.choice(UTTERANCES)
    previous = rng.choice([line for line in UTTERANCES if line != target])
    earlier = rng.choice([line for line in UTTERANCES if line not in (target, previous)])
    verb = rng.choice(SPEECH_VERBS)
    other_verb = rng.choice(SPEECH_VERBS)
    action = rng.choice(ACTIONS)
    target_quote = quoted(rng, target)

    if scenario == "tag_after":
        scene = f"Target dialogue: {target_quote}, {speaker} {verb}. {other} {action}."
    elif scenario == "tag_before":
        scene = f"{speaker} {verb}, Target dialogue: {target_quote}. {other} listened."
    elif scenario == "pronoun_after":
        pronoun = rng.choice(("he", "she", "they"))
        scene = (
            f"{speaker} turned to {other}. Target dialogue: {target_quote}, "
            f"{pronoun} {verb}. {other} did not answer."
        )
    elif scenario == "action_before":
        scene = (
            f"{other} waited by the door. {speaker} {action}. "
            f"Target dialogue: {target_quote}"
        )
    elif scenario == "action_after":
        scene = (
            f"{other} watched in silence. Target dialogue: {target_quote} "
            f"{speaker} {action}."
        )
    elif scenario == "alternating":
        scene = (
            f"{speaker} {verb}, {quoted(rng, earlier)} "
            f"{other} {other_verb}, {quoted(rng, previous)} "
            f"Target dialogue: {target_quote}"
        )
        speaker, other = speaker, other
    elif scenario == "alternating_three":
        scene = (
            f"{speaker}, {other}, and {third} were alone. "
            f"{speaker} {verb}, {quoted(rng, earlier)} "
            f"{other} {other_verb}, {quoted(rng, previous)} "
            f"Target dialogue: {target_quote}, {speaker} added."
        )
    elif scenario == "interrupted":
        scene = (
            f"Target dialogue: {quoted(rng, target + '—')}\n"
            f"{other} interrupted, {quoted(rng, previous)}\n"
            f"{speaker} finished the thought."
        )
    elif scenario == "paragraph_continuation":
        scene = (
            f"{speaker} {verb}, {quoted(rng, earlier)} "
            f"{quoted(rng, previous)} Target dialogue: {target_quote}"
        )
    else:
        raise ValueError(f"Unknown scenario: {scenario}")

    return scene, speaker, other


def build_examples(
    seed: int,
    names: tuple[str, ...],
    scene_count: int,
) -> list[PairExample]:
    rng = random.Random(seed)
    scenarios = (
        "tag_after",
        "tag_before",
        "pronoun_after",
        "action_before",
        "action_after",
        "alternating",
        "alternating_three",
        "interrupted",
        "paragraph_continuation",
    )
    examples: list[PairExample] = []
    for index in range(scene_count):
        scenario = scenarios[index % len(scenarios)]
        scene, speaker, negative = create_scene(rng, names, scenario)
        examples.append(PairExample(scene, speaker, 1, scenario))
        examples.append(PairExample(scene, negative, 0, scenario))
    rng.shuffle(examples)
    return examples


@torch.inference_mode()
def evaluate(model, loader: DataLoader, device: torch.device) -> tuple[float, float]:
    model.eval()
    correct = 0
    total = 0
    loss_sum = 0.0
    for batch in loader:
        batch = {key: value.to(device) for key, value in batch.items()}
        output = model(**batch)
        loss_sum += output.loss.item() * batch["labels"].shape[0]
        correct += (output.logits.argmax(dim=-1) == batch["labels"]).sum().item()
        total += batch["labels"].shape[0]
    return correct / max(total, 1), loss_sum / max(total, 1)


@torch.inference_mode()
def print_probe_scores(model, tokenizer, device: torch.device) -> None:
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
    model.eval()
    for scene, candidates in probes:
        encoded = tokenizer(
            [scene] * len(candidates),
            [f"Candidate speaker: {candidate}" for candidate in candidates],
            max_length=MAX_LENGTH,
            truncation=True,
            padding="max_length",
            return_tensors="pt",
        )
        encoded = {key: value.to(device) for key, value in encoded.items()}
        scores = model(**encoded).logits.softmax(dim=-1)[:, 1].cpu().tolist()
        print("Probe:", ", ".join(f"{name}={score:.4f}" for name, score in zip(candidates, scores)))


def export_onnx(model, tokenizer, output_dir: Path) -> Path:
    model = model.eval().cpu()
    sample = tokenizer(
        "Alice said, Target dialogue: \u201cWe should go now.\u201d",
        "Candidate speaker: Alice",
        max_length=MAX_LENGTH,
        truncation=True,
        padding="max_length",
        return_tensors="pt",
    )
    inputs = (
        sample["input_ids"],
        sample["attention_mask"],
        sample.get("token_type_ids", torch.zeros_like(sample["input_ids"])),
    )
    float_model = output_dir / "speaker_attribution_fp32.onnx"
    torch.onnx.export(
        model,
        inputs,
        float_model,
        input_names=("input_ids", "attention_mask", "token_type_ids"),
        output_names=("logits",),
        dynamic_axes={
            "input_ids": {0: "batch"},
            "attention_mask": {0: "batch"},
            "token_type_ids": {0: "batch"},
            "logits": {0: "batch"},
        },
        opset_version=17,
        do_constant_folding=True,
        dynamo=False,
    )

    quantized_model = output_dir / "speaker_attribution_quantized.onnx"
    quantize_dynamic(
        model_input=float_model,
        model_output=quantized_model,
        weight_type=QuantType.QUInt8,
        per_channel=False,
        op_types_to_quantize=("MatMul", "Gemm", "Gather"),
    )
    return quantized_model


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--train-scenes", type=int, default=6_000)
    parser.add_argument("--validation-scenes", type=int, default=900)
    parser.add_argument("--epochs", type=int, default=3)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--learning-rate", type=float, default=3e-5)
    args = parser.parse_args()

    random.seed(SEED)
    torch.manual_seed(SEED)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(SEED)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Training on {device}")

    args.output_dir.mkdir(parents=True, exist_ok=True)
    tokenizer = AutoTokenizer.from_pretrained(MODEL_ID, use_fast=True)
    model = AutoModelForSequenceClassification.from_pretrained(
        MODEL_ID,
        num_labels=2,
        id2label={0: "NOT_SPEAKER", 1: "SPEAKER"},
        label2id={"NOT_SPEAKER": 0, "SPEAKER": 1},
    ).to(device)

    train_examples = build_examples(SEED, TRAIN_NAMES, args.train_scenes)
    validation_examples = build_examples(
        SEED + 1,
        VALIDATION_NAMES,
        args.validation_scenes,
    )
    train_loader = DataLoader(
        EncodedPairs(tokenizer, train_examples),
        batch_size=args.batch_size,
        shuffle=True,
        pin_memory=device.type == "cuda",
    )
    validation_loader = DataLoader(
        EncodedPairs(tokenizer, validation_examples),
        batch_size=args.batch_size * 2,
        pin_memory=device.type == "cuda",
    )

    optimizer = AdamW(model.parameters(), lr=args.learning_rate, weight_decay=0.01)
    total_steps = len(train_loader) * args.epochs
    scheduler = get_linear_schedule_with_warmup(
        optimizer,
        num_warmup_steps=max(1, math.floor(total_steps * 0.08)),
        num_training_steps=total_steps,
    )
    scaler = torch.amp.GradScaler("cuda", enabled=device.type == "cuda")

    started = time.perf_counter()
    best_accuracy = -1.0
    best_dir = args.output_dir / "checkpoint"
    for epoch in range(1, args.epochs + 1):
        model.train()
        running_loss = 0.0
        for step, batch in enumerate(train_loader, start=1):
            batch = {key: value.to(device, non_blocking=True) for key, value in batch.items()}
            optimizer.zero_grad(set_to_none=True)
            with torch.amp.autocast(
                device_type=device.type,
                dtype=torch.float16,
                enabled=device.type == "cuda",
            ):
                output = model(**batch)
                loss = output.loss
            scaler.scale(loss).backward()
            scaler.unscale_(optimizer)
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            scaler.step(optimizer)
            scaler.update()
            scheduler.step()
            running_loss += loss.item()
            if step % 100 == 0 or step == len(train_loader):
                print(
                    f"Epoch {epoch}/{args.epochs} step {step}/{len(train_loader)} "
                    f"loss={running_loss / step:.4f}",
                    flush=True,
                )

        accuracy, validation_loss = evaluate(model, validation_loader, device)
        print(
            f"Epoch {epoch}: validation accuracy={accuracy:.4f}, "
            f"loss={validation_loss:.4f}",
            flush=True,
        )
        print_probe_scores(model, tokenizer, device)
        if accuracy > best_accuracy:
            best_accuracy = accuracy
            if best_dir.exists():
                shutil.rmtree(best_dir)
            model.save_pretrained(best_dir)
            tokenizer.save_pretrained(best_dir)

    print(f"Training completed in {(time.perf_counter() - started) / 60:.1f} minutes")
    model = AutoModelForSequenceClassification.from_pretrained(best_dir)
    quantized_model = export_onnx(model, tokenizer, args.output_dir)
    vocabulary = tokenizer.get_vocab()
    tokens_by_id = [token for token, _ in sorted(vocabulary.items(), key=lambda item: item[1])]
    (args.output_dir / "vocab.txt").write_text(
        "\n".join(tokens_by_id) + "\n",
        encoding="utf-8",
    )
    metadata = {
        "baseModel": MODEL_ID,
        "maxLength": MAX_LENGTH,
        "positiveLabelIndex": 1,
        "recommendedThreshold": 0.72,
        "minimumMargin": 0.12,
        "syntheticTrainingScenes": args.train_scenes,
        "syntheticValidationScenes": args.validation_scenes,
        "validationAccuracy": best_accuracy,
        "seed": SEED,
    }
    (args.output_dir / "speaker_attribution.json").write_text(
        json.dumps(metadata, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Exported {quantized_model} ({quantized_model.stat().st_size / 1_048_576:.1f} MiB)")


if __name__ == "__main__":
    main()
