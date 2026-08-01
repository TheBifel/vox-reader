# Model adapter guidance

Keep provider transport separate from the labeling contract. The adapter is responsible for packet submission, retries, JSONL capture, and accounting; `sidecar_tool.py` remains responsible for file integrity.

## Sequential interactive models

Send packets in numeric order in one continuing session. After each response:

1. Parse JSONL.
2. Update the global registry.
3. Carry forward the last dialogue turns and unresolved identities.
4. Retry only malformed or missing phrase IDs.

## Stateless or batch APIs

Use two passes:

1. Run character discovery concurrently per chapter, then reconcile all proposals centrally.
2. Run attribution concurrently per complete chapter with the reconciled registry, scene roster, neighboring boundary context, and the provider-neutral prompt. Split only oversized chapters; retain one worker and its scene/dialogue state across their sequential parts.

For Anthropic or another batch API, use one request `custom_id` per chapter/part and retain the packet filename in that ID. Results may arrive out of order; associate them by ID, never result position.

Batching completely independent text slices without the shared registry is not acceptable for final attribution because identity crosses boundaries.

## Agent orchestration

- Match concurrency to the available agent/API slots.
- Use the cheapest capable model and low reasoning for chapter-local discovery/attribution. Escalate only ambiguous or failed chapters.
- Give each worker a disjoint owned phrase range.
- Allow read-only overlap through `context_before` and `context_after`.
- Require workers to write distinct output files.
- Require a concise progress event after every completed chapter. Aggregate those events into periodic user-visible milestones.
- Keep the coordinator responsible for registry reconciliation, retry selection, merge, and final validation.
- If a chapter ends mid-dialogue, flag its boundary for a small sequential repair pass after the parallel merge.

## Privacy and cost

- Obtain user approval before uploading copyrighted book text.
- Send the minimum context required for reliable attribution.
- Do not log API keys or raw book packets.
- Save provider request IDs and phrase ranges, not full prompt bodies, in ordinary logs.
- Resume from completed packets after failure instead of resending the whole book.

## Output repair

Do not silently reinterpret malformed model output. Reject records with unknown phrase IDs, invalid enums, inconsistent profiles, duplicate phrase IDs, or out-of-range values. Ask the provider to regenerate only the affected records.
