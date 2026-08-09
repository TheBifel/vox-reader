---
name: voxreader-label-book
description: Analyze an EPUB novel or a VoxReader speaker-analysis JSON file, distinguish narration from direct speech, resolve each spoken phrase to a stable character identity, choose simple reusable voice profiles, and produce a validated VoxReader JSON sidecar. Use for manual, Codex/sub-agent, Claude Haiku, or other model-assisted whole-book speaker preanalysis and cached character highlighting.
---

# VoxReader Book Labeler

Produce a portable speaker-attribution sidecar without changing phrase boundaries or book text. Treat model output as untrusted decisions; let the bundled script assemble and validate the JSON.

## Required workflow

1. If an external preparation tool supplied an immutable base JSON for the exact book, use it as the phrase manifest and preserve every identity field.
2. Otherwise run direct-EPUB preparation mode. VoxReader has no JSON export action, so direct preparation is the normal repository workflow. Treat its output as provisional and validate it by importing it into VoxReader because sentence-boundary behavior can differ by platform.
3. Keep the input book and generated work directory outside version control. Never add copyrighted book text to the skill.
4. Read [references/attribution-protocol.md](references/attribution-protocol.md) before labeling. When integrating a model API, also read [references/model-adapters.md](references/model-adapters.md).
5. Prepare chapter packets:

   ```powershell
   python scripts/sidecar_tool.py prepare <book.json-or.epub> --out-dir <private-work-dir> --by-chapter
   ```

6. Run the chapter-queue workflow below. Each worker writes to its own file; never let workers append to one shared file.
7. Merge the decision directory into the immutable base:

   ```powershell
   python scripts/sidecar_tool.py merge <private-work-dir>/base.json <private-work-dir>/decisions --output <book>.speakers.json --provider-id <provider> --registry <private-work-dir>/registry.json
   ```

8. Validate independently:

   ```powershell
   python scripts/sidecar_tool.py validate <book>.speakers.json --base <private-work-dir>/base.json
   ```

9. Review only chapters that contain one or more `unknown` phrases after first-pass attribution. Do not run a blanket review pass for chapters with zero `unknown` phrases. Use a Luna-model subagent for every required review or review-repair invocation. After any reviewer decisions are merged, use the deterministic unknown-dialogue audit to select targeted repairs:

   ```powershell
   python scripts/sidecar_tool.py audit <book>.speakers.json
   ```

   `audit` is conservative: it flags likely dialogue labelled `unknown`, using quotation marks, dialogue dashes, nearby speech tags, and multilingual speech verbs. It is a retry trigger, not a speaker-attribution engine; reviewers still use full context.

### Packet sizing

`prepare --by-chapter` keeps each chapter in one packet by default (`--chunk-size 0`). Whole-chapter context is the normal operating mode. Use a positive `--chunk-size` only when an unusually large chapter exceeds the selected model's context limit. Split only at logical scene or paragraph boundaries when a provider adapter can do so; keep the same worker on all parts and carry forward the scene roster, open dialogue state, recent turns, and unresolved references.

10. Report phrase coverage, unknown count, review/retry count, character count, and validation status. Do not claim completion when validation fails or when a chapter containing `unknown` phrases has not completed its required review.

## Progress reporting

Treat long book analysis as an observable job:

1. Immediately report the detected chapter count, packet count, phrase count, and worker count after preparation.
2. Report when discovery begins and when the shared registry is ready, including its character count.
3. Require every worker to send a short coordinator update after each chapter: `chapter N discovery complete`, `chapter N attribution complete (assigned/total, unknown)`, or `chapter N review complete (accepted/repaired/escalated, unknown)`.
4. Have the coordinator aggregate worker updates and report meaningful milestones to the user, normally every 2-4 completed chapters or roughly every minute. Include the number of chapters with `unknown` phrases waiting for Luna review and repair; do not stream one message per phrase.
5. Run the deterministic status command whenever progress needs to be reconstructed:

   ```powershell
   python scripts/sidecar_tool.py status <private-work-dir>
   ```

6. Immediately report retries, validation failures, excessive unknown dialogue, quote-turn conflicts, or stalled chapters. Finish with coverage and validation, not merely "all workers finished."

## Attribution requirements

- Label narrative prose and attribution tags as `narrator`. In `"Go," Lindon said.`, only `"Go,"` belongs to Lindon; `Lindon said.` belongs to the narrator.
- Attribute only direct speech to a character. Keep internal thoughts and free indirect discourse as narrator unless the book explicitly presents them as spoken dialogue.
- Treat a balanced quotation as one speech turn: every phrase inside its opening and closing quotation marks keeps the same speaker. A speaker change inside that span is allowed only when the text itself unmistakably opens a nested quoted turn or gives explicit attribution for a new speaker.
- Do not require quotation marks to recognize direct speech. Dialogue may be indicated by a dialogue dash, a new paragraph, a speech tag, response structure, scene roster, or other contextual evidence. Treat punctuation as evidence, not a prerequisite.
- If a quotation remains open across phrases or paragraphs, strongly prefer the same speaker until its closing mark. Do not wait blindly for a closing mark: if the author appears to have omitted one, use paragraph structure, speech tags, and scene context to decide whether the turn has ended.
- Never assign narration, action prose, or a speech tag to a character merely because it sits next to their quotation.
- Use stable lowercase IDs such as `lindon` or `wei-shi-lindon`; reuse IDs across aliases and titles.
- Use `unknown` when context is genuinely insufficient. Do not turn a guess into a persistent cache entry.
- `narrator` and `unknown` are reserved system IDs, never names discovered from a book. Always use their fixed profiles from the attribution protocol; all other IDs are dynamic book characters.
- Keep each speaker's name, voice group, slot, pitch, speech rate, and optional structured traits identical throughout the book.
- Maintain an evidence-backed `character_profile` for each known character: a compact summary, controlled speech-style fields, and supporting phrase IDs. Use it only as a weak tie-breaker after direct textual and scene evidence; it must never override an explicit tag, quotation/paragraph continuity, or a conflicting local context.
- Use `NARRATOR`, `MALE`, `FEMALE`, or `NEUTRAL`. Infer a gendered voice only from textual evidence or established character information, not from a name alone.
- Preserve every phrase ID exactly and emit one decision per phrase.

## Optional emotion annotation

Add `emotion` only when the phrase's delivery is strongly established by explicit wording or
unambiguous local context. Omit the field for neutral, weak, mixed, or inferred-from-mood cases.
Do not label every phrase merely because the surrounding scene is emotional.

Use this compact object:

```json
"emotion": {
  "type": "CURIOSITY",
  "intensity": 0.65,
  "confidence": 0.91
}
```

Use only these controlled values:

```text
JOY
SADNESS
ANGER
FEAR
SURPRISE
DISGUST
TENSION
EXCITEMENT
CURIOSITY
CONFIDENCE
AFFECTION
SHAME
GRIEF
AMUSEMENT
FRUSTRATION
RELIEF
DETERMINATION
```

`intensity` and `confidence` are JSON numbers in `0..1`. Require emotion confidence of at least
`0.85`; otherwise omit the complete `emotion` field. Emotion confidence is independent of speaker
attribution confidence.

Phrase with confidently determined emotion:

```json
{
  "phrase_id": "7c10...",
  "phrase_index": 158,
  "start": 8863,
  "end": 8881,
  "text_hash": "fd09...",
  "text": "\"What about Gold?\"",
  "speaker_id": "lindon",
  "speaker_name": "Lindon",
  "voice_group": "MALE",
  "voice_slot": 1,
  "pitch": 0.94,
  "speech_rate": 1.0,
  "confidence": 0.99,
  "evidence": "CONTEXTUAL",
  "source": "PREANALYSIS_CACHE",
  "emotion": {
    "type": "CURIOSITY",
    "intensity": 0.65,
    "confidence": 0.91
  }
}
```

Neutral or uncertain phrase: omit `emotion` entirely.

```json
{
  "phrase_id": "7dc3...",
  "phrase_index": 159,
  "start": 8882,
  "end": 8899,
  "text_hash": "ba62...",
  "text": "he asked.",
  "speaker_id": "narrator",
  "speaker_name": "Narrator",
  "voice_group": "NARRATOR",
  "voice_slot": 0,
  "pitch": 1.0,
  "speech_rate": 0.96,
  "confidence": 1.0,
  "evidence": "EXPLICIT_TAG",
  "source": "PREANALYSIS_CACHE"
}
```

## Model portability

The model receives JSON work packets and returns JSONL decisions using the contract in the protocol reference. The same contract is suitable for Haiku, another hosted model, or a Codex sub-agent. Provider-specific code must stay outside the semantic contract.

For small tests, label packets directly. For a full book, use the coordinated discovery, attribution, conditional review, and repair workflow below. A first-pass model is a proposer; chapters with no `unknown` phrases may proceed directly to merge after structural validation and audit.

### Round 1: bounded parallel discovery

1. Create a FIFO queue containing one job per chapter. Start up to the available worker limit, but assign exactly one chapter at a time. When a worker finishes, give it the next unclaimed chapter; never pre-assign a multi-chapter bundle.
2. Prefer the least expensive capable model with low reasoning for ordinary chapters. Reserve a stronger model for registry reconciliation, repair, and chapters that fail review twice. Never send the whole book to one worker.
3. Give each worker one complete chapter and its boundary context. Ask for character/alias proposals, evidence-backed traits, character profiles, speech-style markers, and scene-local ambiguities. Do not ask for the final sidecar.
4. Reconcile all proposals in one coordinator pass into `<work>/registry.json`. Assign canonical IDs, one fixed voice profile, and one evidence-backed character profile per identity. Preserve unresolved aliases as separate or unknown; do not merge by name similarity alone.

### Round 2: chapter-at-a-time attribution

1. Refill workers from the same one-chapter FIFO pattern. A worker receives the complete chapter by default. Only an oversized chapter may have sequential parts; one worker owns it and retains its local scene roster, dialogue/turn state, and unresolved references across those parts.
2. Give every worker the reconciled registry, the attribution protocol, and its one chapter packet with before/after context.
3. Have each worker write exactly one decision per owned phrase to `decisions/chapter-0007.jsonl`. Preserve the packet's owned phrase list; do not emit decisions for context-only phrases.
4. The coordinator rejects malformed, incomplete, duplicate, or profile-inconsistent files immediately and returns only that chapter to the queue.

### Round 3: conditional independent chapter review

1. Queue only attributed chapters containing one or more `unknown` phrases. Do not review a chapter with zero `unknown` phrases solely for routine quality assurance. The reviewer must use the Luna model and be a different worker/model invocation from the author whenever possible.
2. Give the reviewer the original chapter packet, boundary context, the global registry, and the candidate chapter JSONL. Do not ask whether it "looks good". Ask it to independently re-attribute every owned phrase, then compare its result to the candidate.
3. The reviewer must specifically inspect every `unknown` likely-dialogue phrase, every candidate speaker change within an open quotation or paragraph dialogue sequence, narration adjacent to dialogue markers, pronoun/alias resolution, and character profile drift. Use the character profile only after textual, turn, and scene evidence; never replace missing evidence with stylistic guessing.
4. The Luna reviewer writes a replacement `review-decisions/chapter-0007.jsonl` with a full corrected decision for every owned phrase, plus a small `reviews/chapter-0007.json` verdict: `accepted`, `repaired`, or `escalated`, counts, and concise reasons. The coordinator uses the reviewer file as the final chapter output when accepted or repaired.
5. Do not let a reviewer change an `unknown` to a character merely to improve metrics. It must have explicit, turn-pattern, or defensible contextual evidence. Conversely, an `unknown` quoted line with recoverable context is a review failure, not an acceptable default.

### Round 4: targeted repair and escalation

1. Automatically return a chapter to repair when structural validation fails, its required Luna review finds any quote-turn conflict, a reviewer changes the candidate, or likely-dialogue unknowns exceed the quality gate.
2. A repair worker receives the original chapter, candidate, reviewer correction, registry, and only the reported issues. It produces a complete replacement chapter file, not a patch with missing rows.
3. Review the repaired chapter again with a fresh Luna invocation. After two failed reviews, escalate that chapter to the coordinator/stronger model with adjacent chapter context; do not silently merge it.
4. A chapter may retain `unknown` only when both independent passes agree that the supplied context cannot defend an identity. Record its phrase IDs and reasons in the final quality report.

### Quality gates before merge

1. Every chapter containing one or more `unknown` phrases must have one completed Luna review verdict. Chapters with zero `unknown` phrases may be merged directly from their structurally valid first-pass files.
2. Structural validation must have zero errors: complete phrase coverage, no duplicate IDs, unchanged immutable text fields, valid enums/ranges, and one consistent profile per character.
3. For reviewed chapters, there must be zero unreviewed speaker switches inside a balanced quotation or paragraph dialogue sequence and zero character-labelled narration/action/tag phrases reported by the Luna reviewer.
4. The coordinator must inspect unknown metrics by chapter, separating all unknown rows from unknown likely-dialogue rows. Any chapter with more than 2 unknown likely-dialogue phrases or more than 2% unknown likely dialogue is automatically repaired and re-reviewed. These are escalation triggers, not permission to guess.
5. The final report lists: total unknowns, unknown likely-dialogue count/rate, chapters repaired, chapters escalated, quote/paragraph-turn conflicts found/fixed, and the remaining unknown phrase IDs with reasons.

This workflow is parallel across chapters but context-aware. The shared registry prevents cross-chapter identity drift; chapter boundary context reduces errors where a speech turn spans a boundary; targeted Luna review concentrates independent checking on the chapters where attribution evidence was insufficient.

## Script behavior

`scripts/sidecar_tool.py` uses only the Python standard library. It supports:

- `prepare`: validate/read canonical JSON or extract a provisional JSON template from an EPUB, then create whole-chapter packets by default (or explicit oversized-chapter parts) and a chapter catalog. Legacy TSV is accepted only as migration input.
- `merge`: preserve optional `emotion`, embed the validated registry when supplied, and enforce immutable phrase data, complete coverage, valid enums/ranges, and globally consistent character profiles.
- `validate`: verify a finished JSON sidecar structurally and optionally compare it with its base template.
- `status`: reconstruct per-chapter attribution progress from decision files without trusting worker reports.
- `audit`: report per-chapter unknown counts and a conservative dialogue-candidate unknown heuristic that does not require quotation marks; exits non-zero when the configured review gate requires repair.

The detailed sidecar contract is in [references/sidecar-schema.md](references/sidecar-schema.md).
