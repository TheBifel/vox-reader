# Attribution protocol

## Discovery record

Each chapter discovery worker returns one JSON document:

```json
{
  "chapter_index": 7,
  "characters": [
    {
      "proposed_id": "lindon",
      "canonical_name": "Lindon",
      "aliases": ["Wei Shi Lindon"],
      "voice_group": "MALE",
      "evidence_phrase_ids": ["<phrase id>"],
      "character_profile": {
        "summary": "Evidence-backed compact description.",
        "speech_style": {
          "register": "FORMAL",
          "verbosity": "TERSE",
          "typical_tones": ["CALM"],
          "markers": ["short replies"]
        },
        "evidence_phrase_ids": ["<phrase id>"]
      }
    }
  ],
  "unresolved_aliases": [
    {"alias": "the young man", "candidate_ids": ["lindon"], "evidence_phrase_ids": ["<phrase id>"]}
  ]
}
```

The coordinator reconciles proposals into a global registry:

```json
{
  "version": 1,
  "characters": [
    {
      "speaker_id": "lindon",
      "speaker_name": "Lindon",
      "aliases": ["Wei Shi Lindon"],
      "voice_group": "MALE",
      "voice_slot": 1,
      "pitch": 0.96,
      "speech_rate": 1.0,
      "character_profile": {
        "summary": "Evidence-backed compact description.",
        "speech_style": {
          "register": "FORMAL",
          "verbosity": "TERSE",
          "typical_tones": ["CALM"],
          "markers": ["short replies"]
        },
        "evidence_phrase_ids": ["<phrase id>"]
      }
    }
  ]
}
```

Use phrase IDs as evidence references so coordinator artifacts do not duplicate book passages. The coordinator must resolve ID/profile conflicts before attribution workers start.

## Decision record

Return newline-delimited JSON. Return one object for every phrase, in input order:

```json
{"phrase_id":"<exact input id>","speaker_id":"lindon","speaker_name":"Lindon","voice_group":"MALE","voice_slot":1,"pitch":0.96,"speech_rate":1.0,"confidence":0.96,"evidence":"EXPLICIT_TAG"}
```

When emotion is explicit or unambiguous with confidence `>= 0.85`, append the optional object:

```json
{"phrase_id":"<exact input id>","speaker_id":"lindon","speaker_name":"Lindon","voice_group":"MALE","voice_slot":1,"pitch":0.96,"speech_rate":1.0,"confidence":0.96,"evidence":"EXPLICIT_TAG","emotion":{"type":"ANGER","intensity":0.8,"confidence":0.93}}
```

Omit `emotion` completely for neutral, uncertain, mixed, or scene-mood-only interpretations. Use
the controlled emotion types and rules defined in `SKILL.md`.

Allowed evidence values:

- `EXPLICIT_TAG`: an adjacent or grammatically attached speech tag names the speaker.
- `QUOTE_CONTINUATION`: the phrase continues the same open speech turn.
- `TURN_PATTERN`: scene turn-taking and adjacency provide the strongest evidence.
- `CONTEXTUAL`: actions, address, knowledge, or wider context identify the speaker.
- `UNKNOWN`: no defensible assignment.

Use this exact unknown record when unresolved:

```json
{"phrase_id":"<exact input id>","speaker_id":"unknown","speaker_name":"Unknown","voice_group":"NEUTRAL","voice_slot":0,"pitch":1.0,"speech_rate":1.0,"confidence":0.0,"evidence":"UNKNOWN"}
```

Use this narrator profile everywhere:

```json
{"speaker_id":"narrator","speaker_name":"Narrator","voice_group":"NARRATOR","voice_slot":0,"pitch":1.0,"speech_rate":0.96}
```

## Reasoning procedure

For each phrase:

1. Determine whether the phrase is direct speech. If not, assign narrator even when it contains a character name or an attribution verb.
2. Check explicit tags before and after the quotation. The quoted span gets the character; the tag remains narrator.
3. Treat a balanced outer quotation as a single speech turn. Every phrase within its opening and closing marks must retain one speaker unless a nested quotation or explicit attribution clearly establishes a new speaker.
4. Do not require quotation marks for direct speech. Detect dialogue from a dialogue dash, a new paragraph, a speech tag, response structure, scene roster, and local context. Continue a paragraph dialogue turn as a strong prior, not an absolute rule.
5. Resolve pronouns and aliases using the current scene roster.
6. Use turn-taking only after explicit evidence. Do not assume strict alternation when more than two characters are present.
7. Use actions near a speech turn as contextual evidence, but do not assign an action sentence to the acting character; it is narration.
8. Use the evidence-backed character profile only as a final, weak tie-breaker between scene candidates. It can adjust confidence or trigger review, but cannot override explicit attribution, dialogue continuity, or conflicting local context.
9. Lower confidence when attribution depends on distant context. Use unknown below a defensible threshold rather than guessing.
10. Annotate emotion only after speaker attribution. Distinguish the speaker's delivery from the
   general mood of the scene, and omit emotion below the required confidence threshold.

## Character registry

Maintain a global registry with:

- canonical `speaker_id`
- display name
- aliases/titles
- voice group
- voice slot
- pitch
- speech rate
- optional structured traits: `age_band`, `gender_presentation`, and `vocal_register`
- optional `character_profile` with compact summary, controlled speech style, and evidence phrase IDs
- first appearance and supporting evidence

Merge aliases only when identity is supported. Never create separate IDs for `Lindon`, `he`, and `Wei Shi Lindon` once they are known to be the same person.

Suggested voice ranges:

| Voice group | Slots | Pitch | Rate |
| --- | ---: | ---: | ---: |
| MALE | 1–4 | 0.88–1.04 | 0.92–1.08 |
| FEMALE | 1–4 | 0.98–1.16 | 0.92–1.08 |
| NEUTRAL | 1–4 | 0.92–1.10 | 0.92–1.08 |

These are differentiation hints, not gender rules. Keep profiles modest and consistent.

## Model prompt

Use this as the stable provider-neutral instruction:

> You label fiction for a cached audiobook reader. Analyze the supplied packet using its context and the supplied global character registry. Output JSONL only, one decision per phrase in the same order. Preserve phrase_id exactly. Assign narrator to all narrative prose and speech tags; assign characters only to direct speech. Dialogue may use quotation marks, dashes, new paragraphs, speech tags, or response structure. Reuse canonical identities and voice profiles. Use character profiles only as weak tie-breakers after direct textual and scene evidence. Use unknown instead of an unsupported guess. Add the optional emotion object only when the phrase's delivery is explicit or unambiguous with confidence at least 0.85; otherwise omit it. Follow the provided enum values and numeric ranges.

Append the packet JSON, current registry, and previous unresolved questions. Validate the returned JSONL before accepting it.

For discovery, replace the instruction with:

> Discover characters and aliases in this chapter for a fiction speaker-attribution registry. Return one JSON document following the discovery schema. Cite evidence by phrase_id, not by copying passages. For each established character, optionally provide only evidence-supported structured traits and a compact character profile with controlled speech style. These are attribution and voice-selection cues, not claims about real identity. Use `UNKNOWN` rather than guessing. Keep uncertain aliases unresolved.

## Independent review protocol

A reviewer is not a proofreader of model prose. It is a second attribution pass with a different
task and, whenever possible, a different worker/model invocation from the original chapter author.
Give it the original packet, its before/after context, the reconciled registry, and the candidate
chapter JSONL. It must emit a complete replacement JSONL file for the chapter, in the same input
order, plus a separate compact verdict object.

Reviewer priorities, in order:

1. Re-derive direct speech versus narration. Remove character attribution from action prose,
   speech tags, and narration even where the character is named.
2. Trace every opening/closing quotation over the whole chapter. A balanced outer quote must have
   a single speaker unless a nested quote or explicit new attribution proves otherwise.
3. Resolve every candidate `unknown` that is likely dialogue using packet context, scene roster,
   adjacent tags, paragraph/dash structure, turn patterns, character profiles, and chapter boundary context. Retain `unknown` only when the
   evidence is genuinely insufficient.
4. Check aliases, pronouns, and fixed voice/trait profiles against the global registry.
5. Compare the independent decisions to the candidate and correct every defensible discrepancy.

The verdict file has this shape; it contains no book text:

```json
{
  "chapter_index": 7,
  "verdict": "repaired",
  "candidate_unknown_direct_speech": 5,
  "final_unknown_direct_speech": 1,
  "quote_turn_conflicts_found": 2,
  "changed_phrase_ids": ["<phrase id>"],
  "remaining_unknown": [
    {"phrase_id": "<phrase id>", "reason": "No speaker tag, roster, or defensible turn pattern."}
  ]
}
```

Allowed verdicts are `accepted`, `repaired`, and `escalated`. Use `escalated` after the reviewer
cannot safely resolve a systemic conflict or a second review still fails the quality gate.

Use this provider-neutral review prompt:

> Independently validate this chapter's cached-fiction speaker attribution. You receive the exact
> phrase packet, boundary context, global character registry, and a candidate JSONL file. Output a
> complete replacement JSONL decision for every owned phrase in order, then a separate compact
> verdict JSON object. Do not rubber-stamp the candidate. Check direct speech versus narration,
> quotation and paragraph-dialogue continuity, every likely-dialogue `unknown`, aliases, turn-taking, and profile consistency.
> Keep `unknown` only when the supplied context cannot defend an identity. Never invent an identity
> merely to reduce the unknown count.
