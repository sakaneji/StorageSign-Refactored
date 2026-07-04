# Documentation Rules

Applies to docs under `docs`; also use for `../README.md` changes.

- Read `workflow-documentation.md`, `documentation-update-guide.md`, `../README.md`, and the closest doc.
- Keep descriptions aligned with the actual code and tests.
- Keep `../README.md` short as the landing page; put details in topic docs.
- Use existing command names, permission nodes, and setting keys.
- Do not invent behavior.
- For feature or behavior changes, update the closest user-facing doc and `test-cases.md`.
- For overview/compatibility, setup/usage, or development/test-entry changes, update `compatibility.md`, `getting-started.md`, or `development.md`.
- For config changes, update `configuration.md`, `../src/main/resources/config.default.yml`, and check whether `../README.md` needs link or summary changes.
- For command or permission changes, update `commands.md`, `../src/main/resources/plugin.yml`, and check whether `../README.md` needs link or summary changes.
- For operations, runtime checks, Logger, or diagnostics changes, update `operations.md` and `runtime-validation-checklist.md`.
- For index/search/`/sswarp`/nearby-display/external CLI-viewer behavior, update `storage-sign-index.md`.
