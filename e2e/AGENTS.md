# E2E Rules

Applies to E2E harnesses, bot scripts, runtime validation fixtures, and saved E2E behavior.

- Read `../docs/workflow-testing.md`, `../docs/runtime-validation-checklist.md`, and `../docs/test-cases.md`.
- `../docs/storage-sign-index.md` when index/search/`/sswarp`/nearby-display behavior is involved.
- Prefer `../scripts/test.sh e2e` or `../scripts/test.sh banner-compat` instead of ad hoc runner calls.
- Batch verification instead of repeatedly polling short runs.
- Treat 26.x E2E and banner-compat paths cautiously until Mineflayer / `minecraft-protocol` support is confirmed for the target version.
- Update `../docs/runtime-validation-checklist.md` when manual or E2E runtime procedures change.
- Update `../docs/test-cases.md` when saved E2E result summaries or covered runtime cases change.
