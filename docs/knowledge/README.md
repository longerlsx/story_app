# Knowledge Base

This directory stores reusable project knowledge that should survive beyond a single conversation thread.

Current entries:

- [2026-04-android-reader-development-cycle.md](/Users/longshengxi/proj/story_app/docs/knowledge/2026-04-android-reader-development-cycle.md): high-level implementation and debugging summary for the current Android reader build-out.
- [gradle-android-environment-troubleshooting.md](/Users/longshengxi/proj/story_app/docs/knowledge/gradle-android-environment-troubleshooting.md): Gradle, Android SDK, proxy, and repository-resolution lessons from project bootstrap.
- [bugs/INDEX.md](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/INDEX.md): structured bug index with links to per-bug JSON records.
- [bugs/TEMPLATE.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/TEMPLATE.json): canonical bug-record template for new entries.

Recommended read order for a new thread:

1. Open this file.
2. Read [bugs/INDEX.md](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/INDEX.md).
3. Open the matching bug JSON by tags or symptom.
4. Only then jump into code or test files.

## Required Maintenance Rule

Bug knowledge is not optional project hygiene. From this point onward:

1. Every confirmed bug fix must create a new JSON record or update an existing matching record in [bugs](/Users/longshengxi/proj/story_app/docs/knowledge/bugs).
2. Every failed debugging branch that reveals a reusable anti-pattern must be captured in `failed_attempts`.
3. Every new or updated bug record must also be reflected in [bugs/INDEX.md](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/INDEX.md).
4. If the bug changes project-level understanding, update the relevant cycle summary document such as [2026-04-android-reader-development-cycle.md](/Users/longshengxi/proj/story_app/docs/knowledge/2026-04-android-reader-development-cycle.md).

If a thread resolves code without updating the knowledge base, the work is considered incomplete.

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
