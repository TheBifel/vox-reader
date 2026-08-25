---
document_type: device_capability_reference
device: Samsung Galaxy S21
test_os: Android 15
evidence_window: 2026-07-30 through 2026-08-10
last_verified: 2026-08-25
update_when: a new on-device model, runtime, workload, or measured benchmark is tested on the S21
---

# Samsung Galaxy S21: observed on-device AI capability

## Purpose and evidence boundary

This document records what the VoxReader project actually established about running AI workloads
on the Samsung Galaxy S21. It is intended as a starting point for future offline/mobile AI projects,
not as a general benchmark of every Galaxy S21 variant.

The tested phone ran Android 15. The repository did not record its chipset, RAM variant, battery
temperature, power draw, or ambient conditions, so this document does not infer them. Timing is
included only where it was observed during testing or is explicitly preserved in project records.
Model size, thread count, and buffering values come from the shipped VoxReader implementation.

## Executive conclusion

The S21 is capable of useful, fully local CPU inference when the model and interaction design are
appropriate:

- Compact neural TTS is practical. A 40-million-parameter KittenTTS Micro model can support
  continuous audiobook-style playback with multiple voices when speech is generated ahead of the
  playhead.
- Piper-class ONNX speech models are also practical and provide a broad offline voice catalog,
  including English and Ukrainian voices.
- Scheduling matters as much as raw inference speed. Generating only after the previous phrase
  ends caused audible waits; one sequential generator running in parallel with audio playback made
  the same advanced model feel smooth.
- Screen-off execution needs explicit Android lifecycle support. A foreground media service and
  partial wake lock were necessary to prevent severe generation slowdown while the phone was
  locked.
- Small text classifiers can run locally, but running is not the same as solving the product task.
  The TinyBERT speaker-attribution experiment was compact enough for the phone but was too slow and
  inaccurate in the real reading flow.
- Live generative speaker reasoning was not viable in this project. It introduced multi-second
  waits, crashes, and unacceptable reading interruptions. Pre-analysis plus a local cache was the
  successful product direction.

For future projects, treat the S21 as a good target for bounded, streaming inference with compact
models—not as evidence that arbitrary local LLM workloads will be interactive.

## Workloads tested

### 1. Android offline TTS: confirmed baseline

VoxReader first used Android's local `TextToSpeech` engine. On the S21 it successfully:

- produced speech without a network dependency;
- switched among character voice profiles using installed voices, pitch, and rate;
- highlighted spoken ranges from Android callbacks;
- queued sentence transitions within a few milliseconds after voice warm-up; and
- continued through the foreground media-playback path with lock-screen controls.

This was the lowest-risk and lowest-latency option, but voice availability and quality depend on the
voices installed on the device. It is a useful fallback even when neural models are available.

### 2. Quantized TinyBERT speaker attribution: executable, not product-usable

The abandoned live-attribution POC bundled a dynamically quantized ONNX model based on
`TinyBERT_General_4L_312D`:

| Property | Tested configuration |
| --- | --- |
| Model file | `speaker_attribution_quantized.onnx` |
| Model size | 14,546,534 bytes, approximately 13.9 MiB |
| Input | WordPiece, fixed 160-token scene/candidate pair |
| CPU execution | ONNX Runtime, 2 intra-op threads, 1 inter-op thread |
| Use | Score nearby candidate characters for ambiguous dialogue |

The model initialized and executed on the phone, proving that this class of compact transformer is
technically deployable. It did not prove useful speaker understanding. During real-book testing we
observed delayed recognition, many sentences collapsing to one voice, and unreliable attribution.
The task also required one or more candidate evaluations for ambiguous text, so end-to-end latency
was more important than a single forward-pass time.

The lesson is not that TinyBERT-sized models are too large for the S21. The lesson is that model
quality, candidate construction, tokenizer work, repeated inference, and placement in the critical
playback path can make a small model unusable. This experiment was removed in favor of validated,
pre-generated speaker maps.

### 3. Live local generative speaker reasoning: rejected for real-time use

Before adopting cached analysis, VoxReader experimented with a more generative local approach for
deciding who was speaking. The exact model artifact and reproducible benchmark configuration were
not retained, so no parameter-count or model-size claim should be made from this experiment.

What was observed is still useful:

- speaker decisions arrived too late for seamless reading;
- delays were visible between phrases and character changes;
- some iterations crashed on the phone; and
- the resulting experience was judged unusable even before attribution quality was fully solved.

This workload mixed language understanding with a hard real-time deadline. Moving analysis outside
playback—overnight, on desktop, or through an explicitly authorized cloud model—and importing a
validated JSON cache removed that deadline entirely.

### 4. Piper through sherpa-onnx: confirmed practical local neural TTS

VoxReader's **AI Simple** path runs sherpa-compatible Piper ONNX models locally. The tested runtime
uses two inference threads and a shared buffered PCM pipeline. The catalog includes single- and
multi-speaker English models plus Ukrainian Lada, Mykyta, and Tetiana options.

The S21 successfully generated and played distinct character voices. After buffering was added,
phrase transitions became suitable for continuous book listening. This established that compact
Piper-class voice models are a realistic choice for future offline speech projects on this device.

There was an important integration failure: raw Piper files are not necessarily binary-compatible
with sherpa's Android runtime. Incompatible model/runtime combinations caused native crashes rather
than clean Kotlin exceptions. The final catalog therefore uses sherpa-converted models and validates
the expected files before initialization.

Practical takeaway: compatibility metadata and a known-good model catalog are part of the runtime
contract. Do not let users point a native inference engine at arbitrary files merely because their
extension is `.onnx`.

### 5. KittenTTS Micro 0.8: confirmed upper working point for this project

VoxReader's **AI Advanced** path runs KittenTTS Micro 0.8 through sherpa-onnx:

| Property | Tested configuration |
| --- | --- |
| Model | KittenTTS Micro 0.8 |
| Advertised parameter count | 40 million |
| Model weights | 41,385,869 bytes |
| Voice embeddings | 3,276,800 bytes |
| Tokens | 1,064 bytes |
| Total download | 44,663,733 bytes, approximately 42.6 MiB |
| Voices | 8 English voices: 4 male and 4 female |
| CPU execution | sherpa-onnx, 2 threads |
| Generation unit | One sentence at a time |

The first straightforward implementation waited for each new phrase. On the S21 this produced
roughly 1–4 seconds of silence between some phrases, which was unacceptable for listening. That
result by itself would have incorrectly suggested that the model was too slow.

The successful implementation changed scheduling rather than the model:

1. one generator preserves native-model safety and phrase order;
2. generation runs concurrently with playback of the previous phrase;
3. the reader requests approximately five speakable phrases ahead;
4. generated PCM is passed to an independent `AudioTrack` playback loop;
5. an eight-entry in-memory PCM LRU makes repeated/recent phrases cheap; and
6. roughly three previous phrase positions remain convenient for backward navigation.

With this pipeline, advanced speech was reported as working extremely well on the S21, including
different character voices. A spinner remains necessary for cold start, seeking far outside the
buffer, or genuine buffer starvation.

This confirms a product-relevant capability: the S21 can sustain compact neural TTS for continuous
listening when average generation throughput stays ahead of audio consumption. It does **not**
establish a measured real-time factor, maximum sentence length, or safe upper parameter count.

### 6. PCM emotion post-processing: implemented, not a device benchmark

VoxReader can apply pitch, speed, loudness, tone, and pause transformations after neural synthesis.
The processing is bounded local PCM work and is covered by deterministic unit tests. The project did
not preserve a controlled S21 benchmark comparing post-processing on versus off, nor a reviewed
emotion-rich book test that proves perceived emotional quality.

Therefore this feature demonstrates architectural feasibility, not a measured S21 performance
result. Future work should record generation time, post-processing time, audio duration, and thermal
behavior separately.

## Screen-off and Android lifecycle observations

Early testing showed a large difference between unlocked and locked-screen generation: speech that
generated quickly while the screen was on could take much longer after locking the phone. A media
notification alone was not sufficient evidence that Android would preserve CPU availability.

The stable path uses:

- a foreground `MediaPlaybackService`;
- an active `MediaSession` and public playback notification;
- `PARTIAL_WAKE_LOCK` while playback is active; and
- release of the wake lock when paused or stopped.

After adopting this structure, background/lock-screen neural playback became a supported workflow.
This is a general Android lesson: any local model that must meet deadlines with the display off
needs an explicit foreground execution contract and measured screen-off tests. Do not assume that a
coroutine, ViewModel, or notification alone protects inference from power management.

## Stability and native-runtime observations

The S21 experienced several crashes during neural voice development. They were integration and
lifecycle findings, not proof that the device lacked compute:

- incompatible or incomplete model packs can terminate native code;
- changing engines or deleting models while work is active requires cancellation and ownership
  discipline;
- invoking one native model concurrently is riskier than a single ordered generator;
- stale callbacks from a previous engine/session must not mutate current playback; and
- model readiness must be established before accepting queued synthesis work.

The final architecture uses one generator per neural pipeline, generation IDs for cancellation,
validated private model directories, an engine registry, bounded look-ahead, and stale-event
rejection. Stability improved enough for extended real-device listening.

## What we can safely reuse in future S21 projects

### Good candidates

- compact ONNX audio models in roughly the tested 14–45 MB artifact range;
- streaming or chunked tasks where work can run ahead of consumption;
- CPU inference using a small fixed thread count such as two;
- multiple small models loaded on demand rather than simultaneously;
- local embeddings/classifiers when they are not placed on a hard real-time UI path;
- deterministic DSP after inference; and
- offline workflows with precomputation and persistent caches.

These are observed patterns, not hard device limits.

### Use caution

- generative language reasoning required before every sentence or UI response;
- arbitrary user-supplied ONNX/model files passed to JNI runtimes;
- simultaneous inference calls against one native model instance;
- cold-start-dependent interaction without a visible preparation state;
- unbounded audio/image/tensor caches; and
- background inference without a foreground service and screen-off validation.

### Prefer precomputation when

- inference can take longer than the user's current audio/text unit;
- the result is stable and reusable, such as speaker attribution for a book;
- correctness benefits from whole-document context or validation passes; or
- a stronger desktop/cloud model can produce a cache that remains useful offline.

## Gaps: what was not measured

Future teams should not treat this document as evidence for any of the following:

- maximum LLM size or context window;
- tokens per second for a local language model;
- NPU or GPU acceleration—the tested neural paths used CPU-oriented runtimes;
- peak RAM, memory pressure, or coexistence of several loaded neural models;
- sustained thermal throttling during hours of generation;
- battery drain per hour;
- performance differences among S21 chipset/RAM variants;
- speech real-time factor by model and sentence length;
- Whisper transcription performance; or
- vision, diffusion, image generation, or multimodal inference.

## Recommended benchmark template for the next project

When evaluating another model on this phone, record:

1. exact device model, chipset/RAM variant, Android build, and battery state;
2. model name/version, parameter count, quantization, file size, and runtime version;
3. CPU/GPU/NPU backend and thread count;
4. cold initialization time and warm initialization time;
5. inference time alongside the amount of output produced;
6. peak memory if available;
7. screen-on and screen-off results;
8. ten- to thirty-minute sustained behavior, temperature, and battery change;
9. cancellation, rapid restart, and model-switch behavior; and
10. product latency with buffering/caching, not only isolated kernel speed.

The most important VoxReader result was that product architecture changed the verdict on the same
model: KittenTTS with per-phrase blocking felt unusable; KittenTTS with look-ahead generation felt
excellent.

## Repository evidence trail

The main reproducible checkpoints behind this reference are:

- `f3f5355` — bundled quantized TinyBERT speaker-attribution POC;
- `1985ce4` — removal of abandoned live inference after real-book evaluation;
- `58029ba` — sentence-ahead TTS buffering;
- `9173b80` and `6d2c30f` — local Piper catalog, foreground playback, compatibility, and stability;
- `5a183e1` — KittenTTS Micro and the shared buffered PCM pipeline;
- `42294f0` — configurable engine-independent speech post-processing; and
- `39f02f7` — controller boundaries, bounded look-ahead, and stale-engine event rejection.

User-observed qualitative results—especially the initial 1–4 second Kitten gaps, smooth buffered
playback, recognition delays, and screen-lock slowdown—were gathered during iterative S21 testing.
They are valuable product observations but are not laboratory benchmarks.
