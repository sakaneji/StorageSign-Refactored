# Test Rules

Applies to Java unit and integration tests under `src/test`.

- Read `../../docs/workflow-testing.md`, `../../docs/test-cases.md`, and `../../docs/test-gap-audit.md`.
- Read `../../docs/runtime-validation-checklist.md` for runtime behavior.
- `../main/AGENTS.md` when tests are paired with implementation changes.
- Prefer `../../scripts/test.sh` over direct Maven commands.
- Use the smallest proving scope: `unit`, `integration`, `coverage`, `e2e`, `banner-compat`; `all` only when needed.
- Batch verification instead of short polling.
- Check environment issues against repo docs before changing code.
- Update `../../docs/test-cases.md` when behavior descriptions, covered cases, or saved-result summaries change.
- For test additions or changed failure conditions, check `../../docs/test-gap-audit.md` as part of the doc update path.
- Refresh `../../docs/test-gap-audit.md` only when a new `coverage` run changes the reported gaps.
