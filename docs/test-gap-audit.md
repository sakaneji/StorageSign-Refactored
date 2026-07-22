# Test Gap Audit

This file lists the saved coverage gaps from the latest stored `target/site/jacoco/jacoco.xml` artifact.
If source changes after that `./scripts/test.sh coverage` run, the entries here are historical artifact summaries:
method names, owning classes, and line numbers can drift or disappear from the current source tree until coverage is rerun and this file is refreshed.

## storagesign

- `storagesign.ConfigLoader.ensureConfigExists` line 143: missed 4, covered 9
- `storagesign.StorageSign.<clinit>` line 55: missed 4, covered 23
- `storagesign.StorageSignCommandTabCompleter.completeSearch` line 54: missed 2, covered 11
- `storagesign.StorageSignCommandTabCompleter.completeGive` line 36: missed 1, covered 3
- `storagesign.StorageSignCommandTabCompleter.completeIndex` line 43: missed 1, covered 6
- `storagesign.StorageSignCommandTabCompleter.onTabComplete` line 25: missed 1, covered 5
- `storagesign.StorageSignCommandTabCompleter.remainingSearchFlags` line 73: missed 1, covered 12
- `storagesign.StorageSignPlugin.getStorageSignQueries` line 275: missed 1, covered 0
- `storagesign.StorageSignPlugin.lambda$retryOminousBanner$0` line 146: missed 1, covered 0
- `storagesign.StorageSignPlugin.retryOminousBanner` line 133: missed 1, covered 14

## storagesign/command

- `storagesign.command.StorageSignSearchCommand.resolveFrontPosition` line 150: missed 4, covered 3
  (saved artifact basis; current source moved this helper to `storagesign.StorageSignFacingSupport.resolveFrontPosition`)
- `storagesign.command.StorageSignSearchCommand.parse` line 60: missed 2, covered 26
- `storagesign.command.StorageSignSearchCommand.formatCoordinateLine` line 137: missed 1, covered 8

## storagesign/search

- `storagesign.search.StorageSignQueryService.filter` line 59: missed 3, covered 17

## storagesign/index

- `storagesign.index.StorageSignChunkRescanScheduler.process` line 56: missed 7, covered 5
- `storagesign.index.StorageSignIndex.distanceSquared` line 403: missed 1, covered 0
- `storagesign.index.StorageSignIndex.elapsedMillis` line 399: missed 1, covered 0
- `storagesign.index.StorageSignIndex.indexPath` line 387: missed 1, covered 0
- `storagesign.index.StorageSignIndex.normalize` line 395: missed 1, covered 0

## storagesign/display

- `storagesign.display.NearbyStorageSignDisplay.processAllocationPending` line 158: missed 4, covered 13

## storagesign/listener

- `storagesign.listener.InventoryListener.withAmount` line 231: missed 1, covered 0

## storagesign/task

- `storagesign.task.ExportSignTask.run` line 74: missed 2, covered 44
