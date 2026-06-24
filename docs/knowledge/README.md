# Knowledge Base

This directory stores reusable project knowledge that should survive beyond a single conversation thread.

Current entries:

- [agent-operating-agreements.md](/Users/longshengxi/proj/story_app/docs/knowledge/agent-operating-agreements.md): required operating agreements for every future work slice, including the initial agreement index, evidence rules, emulator/Computer Use expectations, subagent lifecycle, tool notes, and the current page-mode focus.
- [2026-04-android-reader-development-cycle.md](/Users/longshengxi/proj/story_app/docs/knowledge/2026-04-android-reader-development-cycle.md): high-level implementation and debugging summary for the current Android reader build-out.
- [gradle-android-environment-troubleshooting.md](/Users/longshengxi/proj/story_app/docs/knowledge/gradle-android-environment-troubleshooting.md): Gradle, Android SDK, proxy, and repository-resolution lessons from project bootstrap.
- [bugs/INDEX.md](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/INDEX.md): structured bug index with links to per-bug JSON records.
- [bugs/TEMPLATE.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/TEMPLATE.json): canonical bug-record template for new entries.

Recommended read order for a new thread, after context compaction, and before
each new work slice:

1. Open this file.
2. Read [agent-operating-agreements.md](/Users/longshengxi/proj/story_app/docs/knowledge/agent-operating-agreements.md), especially `Initial Agreement Index`, `Current Priority`, `Emulator And UI Evidence`, `Subagent Use`, and `Git Cadence`.
3. Read [bugs/INDEX.md](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/INDEX.md).
4. Open the matching bug JSON by tags or symptom.
5. Read relevant specs/plans under [docs/superpowers/specs](/Users/longshengxi/proj/story_app/docs/superpowers/specs) and [docs/superpowers/plans](/Users/longshengxi/proj/story_app/docs/superpowers/plans).
6. Only then jump into code or test files.

Do not rely on chat memory for these agreements. Re-reading this path is the
mechanism that keeps later turns aligned with the original objective.

## Required Maintenance Rule

Bug knowledge is not optional project hygiene. From this point onward:

1. Every confirmed bug fix must create a new JSON record or update an existing matching record in [bugs](/Users/longshengxi/proj/story_app/docs/knowledge/bugs).
2. Every failed debugging branch that reveals a reusable anti-pattern must be captured in `failed_attempts`.
3. Every new or updated bug record must also be reflected in [bugs/INDEX.md](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/INDEX.md).
4. If the bug changes project-level understanding, update the relevant cycle summary document such as [2026-04-android-reader-development-cycle.md](/Users/longshengxi/proj/story_app/docs/knowledge/2026-04-android-reader-development-cycle.md).

If a thread resolves code without updating the knowledge base, the work is considered incomplete.

## Required Operating Memory Rule

Operational agreements and reusable capabilities must be written down when they
become important to future turns.

1. If the user adds or corrects a standing agreement, update
   [agent-operating-agreements.md](/Users/longshengxi/proj/story_app/docs/knowledge/agent-operating-agreements.md).
2. If a new tool, plugin, emulator capability, video/screenshot method, corpus
   path, or verification technique becomes part of the workflow, record it in
   the same file unless it belongs more narrowly to a bug JSON.
3. If an experience is tied to a confirmed bug or reusable failed path, record
   it in the matching bug JSON and reflect it in [bugs/INDEX.md](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/INDEX.md).
4. Documentation and test records remain retrieval aids. Final decisions still
   require current facts from code, commands, runtime behavior, logs,
   screenshots, or recordings.

## Required Planning And Review Rule

Plans and implementation work require subagent review checkpoints.

1. Every implementation plan must be reviewed by at least one subagent before execution starts.
2. The plan-review subagent must review the written plan itself, not the conversation history, and must return a clear `Approved` or `Issues Found` conclusion.
3. If the plan reviewer reports `Issues Found`, revise the plan and re-run review until it is approved or the issue is explicitly escalated to the user.
4. After code writing is complete, launch two dedicated review subagents before final completion:
   - one code-review subagent for architecture, correctness, regressions, and maintainability;
   - one test-completeness subagent for missing cases, insufficient assertions, and verification gaps.
5. Valid findings from either implementation reviewer must be addressed or explicitly documented before the task is considered complete.

## Required Subagent Lifecycle Rule

Subagents must not be left open after they finish their assigned work.

1. Every task that launches a subagent must record the subagent's purpose and wait for its final result before relying on its output.
2. After a subagent reaches a completed, failed, or no-longer-needed state, close it explicitly with the available close/release mechanism in the current harness.
3. If multiple subagents are launched for review, close every finished reviewer before reporting the task as complete.
4. A task that used subagents but leaves them running or completed-but-unclosed is considered incomplete.
