# Test Gap Audit

This file lists coverage gaps transcribed from the saved 2026-07-25 coverage summary.
The source `target/site/jacoco/jacoco.xml` artifact is not retained in the current worktree.
The entries below are historical reference data until `./scripts/test.sh coverage` is rerun:
method names, owning classes, and line numbers can drift or disappear from the current source tree and must not be treated as current coverage evidence.

## storagesign

- `storagesign.StorageSignPlugin.retryOminousBanner` line 134: missed 1, covered 14
- `storagesign.StorageSignPlugin.canonicalizeOminousBannerMeta` line 220: missed 2, covered 6
- `storagesign.StorageSignPlugin.getStorageSignQueries` line 311: missed 1, covered 0
- `storagesign.StorageSignPlugin.lambda$retryOminousBanner$0` line 147: missed 1, covered 0
- `storagesign.StorageSign.<clinit>` line 55: missed 4, covered 23
- `storagesign.ConfigLoader.ensureConfigExists` line 147: missed 7, covered 16
- `storagesign.ConfigLoader.copyAtomically` line 182: missed 2, covered 7
- `storagesign.StorageSignFacingSupport.resolveFrontPosition` line 45: missed 3, covered 4
- `storagesign.StorageSignCommandTabCompleter.onTabComplete` line 25: missed 1, covered 6
- `storagesign.StorageSignCommandTabCompleter.completeGive` line 37: missed 1, covered 3
- `storagesign.StorageSignCommandTabCompleter.completeIndex` line 44: missed 1, covered 6
- `storagesign.StorageSignCommandTabCompleter.completeSearch` line 55: missed 2, covered 11
- `storagesign.StorageSignCommandTabCompleter.completeWarp` line 74: missed 1, covered 5
- `storagesign.StorageSignCommandTabCompleter.remainingSearchFlags` line 84: missed 1, covered 12

## storagesign/command

- `storagesign.command.StorageSignSearchCommand.parse` line 58: missed 2, covered 26
- `storagesign.command.StorageSignSearchCommand.formatCoordinateLine` line 135: missed 1, covered 8
- `storagesign.command.StorageSignWarpCommand.onCommand` line 36: missed 11, covered 25
- `storagesign.command.StorageSignWarpCommand.sendUsage` line 89: missed 5, covered 0

## storagesign/search

- `storagesign.search.StorageSignQueryService.filter` line 79: missed 3, covered 17

## storagesign/index

- `storagesign.index.StorageSignChunkRescanScheduler.process` line 56: missed 7, covered 5
- `storagesign.index.StorageSignIndex.indexPath` line 425: missed 1, covered 0
- `storagesign.index.StorageSignIndex.normalize` line 433: missed 1, covered 0
- `storagesign.index.StorageSignIndex.elapsedMillis` line 437: missed 1, covered 0
- `storagesign.index.StorageSignIndex.distanceSquared` line 441: missed 1, covered 0

## storagesign/display

- `storagesign.display.NearbyStorageSignDisplay.processAllocationPending` line 159: missed 4, covered 13

## storagesign/listener

- `storagesign.listener.InventoryListener.withAmount` line 231: missed 1, covered 0

## storagesign/task

- `storagesign.task.ExportSignTask.run` line 74: missed 8, covered 42
