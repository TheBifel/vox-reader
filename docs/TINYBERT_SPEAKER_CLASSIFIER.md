# VoxReader TinyBERT speaker classifier

## Purpose

VoxReader uses a compact encoder classifier for dialogue that cannot be resolved confidently by
deterministic rules. The classifier does not generate character names and does not have
book-specific output classes. Instead, it scores a short scene against each candidate:

```text
sequence A: nearby text with `Target dialogue:` before the phrase
sequence B: `Candidate speaker: <dynamic book character>`
output: NOT_SPEAKER or SPEAKER
```

This formulation supports names the model never saw during training. Explicit tags such as
`Lindon said`, narration, open-quotation continuation, and same-paragraph continuation bypass the
model. Low-confidence classification also returns to the rule result, so inference never needs to
delay or fail playback.

## Base model and runtime

- Base checkpoint:
  [`huawei-noah/TinyBERT_General_4L_312D`](https://huggingface.co/huawei-noah/TinyBERT_General_4L_312D)
- Architecture: four-layer TinyBERT, 312 hidden dimensions
- Input length: 160 WordPiece tokens
- Android runtime: ONNX Runtime
- Packaged model: `speaker_attribution_quantized.onnx`
- Packaged model size: approximately 14 MB
- Quantization: dynamic unsigned 8-bit weights for `MatMul`, `Gemm`, and `Gather`
- Android candidate batch: at most six candidates in one inference

The source project associated with the checkpoint contains Apache-2.0 notices, but the hosted
checkpoint's model card does not currently declare a machine-readable license field. Confirm the
weight-distribution terms before publishing a production app outside this POC.

## Training data

`tools/train_speaker_classifier.py` generates its training and validation scenes. No EPUB or
user-book text is included. Generated patterns cover:

- speaker tags before and after dialogue;
- nearby subject pronouns;
- action beats before and after dialogue;
- two- and three-character exchanges;
- interrupted dialogue; and
- same-paragraph continuations.

The current checkpoint used 4,000 generated training scenes. Every scene produces one positive and
one negative candidate pair. Validation used 600 separately generated scenes with held-out names,
including Lindon and Yerin. One training epoch was retained because the held-out result had already
converged and additional synthetic epochs would add overfitting risk without more evidence.

## Reproduction

Use a task-specific Python environment containing PyTorch, Transformers, ONNX, and ONNX Runtime:

```powershell
python tools/train_speaker_classifier.py `
  --output-dir <working-output-directory> `
  --train-scenes 4000 `
  --validation-scenes 600 `
  --epochs 1 `
  --batch-size 32

python tools/evaluate_speaker_classifier.py `
  --model-dir <working-output-directory> `
  --validation-scenes 600
```

Copy only the selected quantized ONNX file and vocabulary into `app/src/main/assets`. Training
checkpoints and the 32-bit ONNX export are build artifacts and must not be packaged.

## Current generated-data results

| Measurement | Result |
| --- | ---: |
| PyTorch binary accuracy | 99.75% |
| Quantized binary accuracy | 99.25% |
| Quantized candidate-ranking accuracy | 99.83% |
| Accepted-decision precision at score 0.72 / margin 0.12 | 100% |
| Accepted-decision coverage | 98.67% |

These figures measure generated templates, not general fiction accuracy. They verify that training,
export, quantization, and dynamic-name scoring work as intended; they are not a production quality
claim.

The quantization choice is evidence-driven. Signed per-channel embedding quantization reduced
candidate-ranking accuracy to 83.17%. Unsigned per-tensor 8-bit quantization retained 99.83% on the
same generated validation set and is therefore the packaged variant.

## Acceptance behavior

For ambiguous dialogue, the analyzer:

1. collects nearby/recent known characters;
2. scores all candidates in one ONNX call;
3. sorts the candidates by `SPEAKER` probability;
4. accepts only if the winner is at least `0.72`; and
5. also requires a margin of at least `0.12` over the runner-up.

Otherwise it keeps the deterministic attribution. Exceptions, malformed tensor output, no
candidates, and only one candidate also use rules. The model never directly chooses gender.
Male/female/neutral voice grouping is persisted at character level and grounded separately from
book evidence.

## Known limitations and next evaluation

- Generated templates are less varied than published fiction.
- Alternating dialogue without action beats can give multiple candidates high scores; the margin
  gate deliberately rejects those decisions.
- Candidate extraction currently depends on previously found names and recent speakers.
- The 160-token scene cannot represent chapter-level relationships or aliases.
- English uncased WordPiece tokenization is not suitable for every language.
- Android TTS voice gender still depends on the device voice catalogue.

Before raising model authority or removing the rule fallback, build a legally usable,
human-reviewed evaluation set of varied fiction scenes. Track accepted precision and coverage
separately, calibrate both thresholds, and include nested quotations, aliases, titles, groups, long
action beats, first-person narration, and multi-language text.
