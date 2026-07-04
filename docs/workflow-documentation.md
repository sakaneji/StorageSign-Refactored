# Documentation Workflow

Read: `AGENTS.md`, `docs/documentation-update-guide.md`, `README.md`, and the doc closest to the change.

## Update rules

- If behavior changes, update the user-facing docs in the same change.
- If config changes, update `src/main/resources/config.default.yml` and the README settings section.
- If commands or permissions change, update `src/main/resources/plugin.yml` and the README command/permission sections.

## Targets

- Landing page / high-level summary: `README.md`
- Overview and compatibility: `docs/compatibility.md`
- Getting started / usage: `docs/getting-started.md`
- Commands and permissions: `docs/commands.md`
- Configuration: `docs/configuration.md`
- Operations and runtime checks: `docs/operations.md`
- Development notes and test entry points: `docs/development.md`
- Index/search/`/sswarp`/nearby display/external CLI-viewer: `docs/storage-sign-index.md`
- Test coverage, behavior descriptions, or saved-result summaries: `docs/test-cases.md`
- Manual runtime checks: `docs/runtime-validation-checklist.md`
- Coverage gaps: `docs/test-gap-audit.md`

## Rules

- Keep descriptions aligned with the code.
- Prefer existing command names and setting keys.
- Do not invent behavior.
- Keep README short and use topic files for details.
- Verify links and finish with `git diff --check`.
