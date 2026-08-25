---
memory_type: index
authority: canonical
last_verified: 2026-08-25
update_when: memory files are added, renamed, or their ownership changes
---

# VoxReader project memory

This directory is the durable handoff between coding sessions. Code remains authoritative for exact behavior; these files explain intent, invariants, state, and operating practice.

## Read order

| Need | Read |
| --- | --- |
| Start or resume any task | [STATE.md](STATE.md), then [PRODUCT.md](PRODUCT.md) |
| Change code or architecture | [ARCHITECTURE.md](ARCHITECTURE.md), [DECISIONS.md](DECISIONS.md), [TECH_DEBT.md](TECH_DEBT.md) |
| Build, test, install, or handle local assets | [WORKFLOW.md](WORKFLOW.md) |
| Change speaker-map JSON | [../reference/SPEAKER_ANALYSIS_JSON.md](../reference/SPEAKER_ANALYSIS_JSON.md) and the book-labeling skill |
| Plan or evaluate on-device AI for the Galaxy S21 | [../reference/GALAXY_S21_ON_DEVICE_AI.md](../reference/GALAXY_S21_ON_DEVICE_AI.md) |
| Plan deferred whole-book analysis | [../plans/WHOLE_BOOK_ANALYSIS.md](../plans/WHOLE_BOOK_ANALYSIS.md) |

## Ownership

- [PRODUCT.md](PRODUCT.md): stable user experience, capabilities, and non-goals.
- [ARCHITECTURE.md](ARCHITECTURE.md): current system boundaries, data flow, and invariants.
- [WORKFLOW.md](WORKFLOW.md): repeatable local development and device procedure.
- [STATE.md](STATE.md): current checkpoint, validation status, limitations, and likely next work.
- [DECISIONS.md](DECISIONS.md): durable choices and why they were made.
- [TECH_DEBT.md](TECH_DEBT.md): acknowledged debt and explicit refactor triggers.

Keep entries factual and compact. Update `last_verified` after checking the relevant code, not merely after editing prose.
