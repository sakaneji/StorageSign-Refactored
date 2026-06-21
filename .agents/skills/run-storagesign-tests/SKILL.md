---
name: run-storagesign-tests
description: Run and diagnose the StorageSign-Refactored test environment through its Docker-only test runner. Use for test execution, regression verification, release checks, MockBukkit plugin-load checks, Paper/Mineflayer E2E checks, or multi-version Minecraft compatibility checks in this repository.
---

# Run StorageSign Tests

Use the repository's `scripts/test.sh` as the only test entrypoint. Do not install Java, Maven, or Node.js on the host.

## Prepare

1. Resolve the repository root with `git rev-parse --show-toplevel` and run every command there.
2. Inspect `git status --short` without altering unrelated user changes.
3. Confirm Docker is available with `docker info`. Report a clear blocker if the daemon is unavailable.

## Select the scope

Honor an explicitly requested scope. Otherwise:

- Run `./scripts/test.sh unit` for fast, server-independent logic checks.
- Run `./scripts/test.sh integration` for plugin startup, command registration, configuration, recipes, or Bukkit/Paper API integration changes.
- Run `./scripts/test.sh coverage` to execute all JUnit tests and generate the JaCoCo HTML/XML/CSV report without enforcing a numeric threshold.
- Run `./scripts/test.sh e2e <version> <logger-mode>` when one supported Minecraft version or Logger configuration is named. Use `with-logger`, `without-logger`, or `both`.
- Run `./scripts/test.sh e2e` for gameplay behavior, listeners, inventory transport, persistence, Logger integration, or independent multi-version compatibility changes. This runs both Logger modes by default.
- Run `./scripts/test.sh banner-compat 1.21.11` for Potion and ominous-banner persistence across the supported 1.21.4 to 1.21.8 to 1.21.11 shared-world path. Use `banner-compat all` only when the held 26.x environments are explicitly being exercised.
- Run `./scripts/test.sh all` when asked to run all tests, perform a release check, or verify the project without a narrower scope.

Supported E2E versions are `1.21.4`, `1.21.8`, `1.21.11`, `26.1.2`, and `26.2`. Paper 26.2 is pinned to an experimental build until Paper publishes a stable channel build. Treat `scripts/test.sh` as authoritative if this list later changes.

## Execute and diagnose

Run the selected command to completion. The E2E suite starts real Paper servers and a Mineflayer client, so allow several minutes.

### Minimize context use

- For E2E and banner compatibility, read the runner's latest `WAIT_HINT`. On `stage=initial`, wait exactly `wait_seconds`, which already includes the saved total estimate plus a 30-second buffer.
- When the latest hint is `stage=minecraft-startup`, wait exactly one minute. This fixed startup interval overrides the broader initial or remaining estimate until the server either starts or reports failure.
- If the command is still running after the initial wait, use the newest `stage=remaining` hint and wait exactly its `wait_seconds`. Repeat from the newest remaining hint until the command exits or fails.
- Keep repeated process waits inside one orchestration call whenever possible. Do not emit empty intermediate polls into model context. If no `WAIT_HINT` is available, use one three-minute fallback wait.
- If one tool wait is capped below `wait_seconds`, consume the requested duration through consecutive capped waits inside that same orchestration call rather than returning each empty wait to the model.
- During implementation, run only the narrowest affected scope. After it passes, run the requested final scope once; do not rerun already successful scopes separately when `all` will immediately repeat them.
- If a final `all` run is planned, do not run a separate `coverage` pass unless coverage itself is being debugged.
- Preserve the structured `PASS` lines for the final report, but do not open successful artifacts or request verbose output.
- On failure, use the runner's bounded failure excerpt first. Open only the single artifact indicated by the diagnosis order below and expand further only when that evidence is insufficient.

The runner is quiet by default. A successful run prints only structured `PASS` summaries; do not read the saved Maven, runner, bot, Paper, or coverage detail files after success. Set `STORAGESIGN_TEST_VERBOSE=1` only when the user explicitly asks for live detailed output.
Failure excerpts default to 40 lines. Increase `STORAGESIGN_FAILURE_TAIL_LINES` only when those lines do not contain the first actionable cause.
Successful E2E durations are averaged in `target/test-artifacts/e2e-timings.tsv`. Do not read or report that cache unless timing behavior itself is under diagnosis.

On failure, start with the failed scope/version/mode named by the runner. Do not read logs for successful cases. Maven logs are under `target/test-artifacts/`; each E2E artifact directory also contains `runner.log` for Docker lifecycle failures.

For an E2E failure:

1. Read `runner.log` only when build, startup, health, restart, or cleanup failed.
2. Read `bot-main.log` when the main gameplay phase failed.
3. Read `bot-restart.log` only when the restart phase ran and failed.
4. Inspect `paper.log` only when the prior log indicates a plugin exception or server lifecycle failure.
5. For upgrade failures, inspect only the failed version's `bot.log`, then `paper.log` if needed.
6. Separate a product failure from a harness, Docker, server-download, or client-protocol failure before proposing a fix.
7. Do not delete failure artifacts before reporting the relevant evidence.

The E2E suite covers external Logger presence, registration, and sink delivery; placement listener behavior; normal and sneak transfers; StorageSign-item merge, division, and export/reimport; use and break permission denial; StorageSign break drops; sign-edit protection; hoppers, hopper minecarts, and world-dispense refill; automatic collection; potion and ominous-banner round trips; and restart persistence. Fresh ominous-banner exports must include all eight patterns, name metadata, and tooltip hiding. During shared-world upgrades, Minecraft's data fixer may remove the old tooltip-hiding flag; require the eight-pattern identity and name to survive, require import to succeed, and require the current-version re-export to restore the flag. Mineflayer custom-Lore sign placement or an unacknowledged upgraded-sign interaction may use the harness to emit a real Bukkit event; distinguish that fallback from a fully acknowledged client packet.

Spigot is not automated. When Spigot validation is requested, follow the manual smoke-test checklist in `README.md` and state that it was not covered by the Paper E2E result.

## Report

Report:

- the exact command or commands run;
- unit and integration test counts;
- each Paper version and Logger mode, including external registration state and whether both main and restart phases passed;
- each ominous-banner upgrade stage and whether the persisted old-version banner was imported and re-exported;
- artifact paths and the first actionable cause for failures;
- anything not tested, especially manual Spigot coverage.

Do not claim success from a build alone. Require the selected test command to exit successfully.
