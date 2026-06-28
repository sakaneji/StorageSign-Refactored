# Documentation Workflow

Read: `AGENTS.md`, `docs/documentation-update-guide.md`, `README.md`, and the doc closest to the change.

## Update rules

- If behavior changes, update the user-facing docs in the same change.
- If config changes, update `src/main/resources/config.default.yml` and the README settings section.
- If commands or permissions change, update `src/main/resources/plugin.yml` and the README command/permission sections.

## Targets

- Feature changes: `README.md`
- Index/search/nearby display: `docs/storage-sign-index.md`
- Test coverage or behavior descriptions: `docs/test-cases.md`
- Manual runtime checks: `docs/runtime-validation-checklist.md`
- Coverage gaps: `docs/test-gap-audit.md`

## Rules

- Keep descriptions aligned with the code.
- Prefer existing command names and setting keys.
- Do not invent behavior.
- Verify links and finish with `git diff --check`.
