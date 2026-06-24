# Test gap audit

This file lists the current coverage gaps from `target/site/jacoco/jacoco.xml` after the latest `./scripts/test.sh coverage` run.

## storagesign

- `storagesign.StorageSign.isSimilar` line 629: missed 4, covered 74
- `storagesign.StorageSignPlugin.onEnable` line 79: missed 4, covered 4
- `storagesign.AmountTransfer.dividedPerSign` line 16: missed 3, covered 7
- `storagesign.StorageSignPlugin.registerListeners` line 276: missed 2, covered 2
- `storagesign.AmountTransfer.accepted` line 10: missed 1, covered 3
- `storagesign.ConfigLoader.load` line 93: missed 1, covered 5
- `storagesign.ConfigLoader.positive` line 190: missed 1, covered 3
- `storagesign.ConfigLoader.readStringMap` line 172: missed 1, covered 9
- `storagesign.StorageSign.fromStoredItem` line 712: missed 1, covered 69
- `storagesign.StorageSign.getContents` line 522: missed 1, covered 47
- `storagesign.StorageSign.resolveMaterialFromIdentifierToken` line 855: missed 1, covered 15
- `storagesign.StorageSignPlugin.createOminousBannerMetaByApi` line 197: missed 1, covered 1
- `storagesign.StorageSignPlugin.loadOminousBanner` line 147: missed 1, covered 1

## storagesign/adjacency

- `storagesign.adjacency.WallHangingSideFacesRule.findMatches` line 47: missed 13, covered 3
- `storagesign.adjacency.WallHangingSideFacesRule.findFirstMatch` line 22: missed 7, covered 9
- `storagesign.adjacency.WallSignBackFaceRule.findMatches` line 37: missed 7, covered 3
- `storagesign.adjacency.StandingAndCeilingHangingRule.findMatches` line 35: missed 6, covered 2
- `storagesign.adjacency.AdjacencyRuleSupport.toMatchIfStorageSign` line 32: missed 5, covered 5
- `storagesign.adjacency.StandingAndCeilingHangingRule.findFirstMatch` line 16: missed 4, covered 4
- `storagesign.adjacency.WallHangingSideFacesRule.leftOf` line 79: missed 4, covered 1
- `storagesign.adjacency.WallHangingSideFacesRule.rightOf` line 89: missed 4, covered 1
- `storagesign.adjacency.WallHangingSideFacesRule.sameBlock` line 72: missed 4, covered 4
- `storagesign.adjacency.AdjacencyRuleSupport.isCeilingHangingSign` line 24: missed 3, covered 1
- `storagesign.adjacency.WallSignBackFaceRule.findFirstMatch` line 21: missed 3, covered 7
- `storagesign.adjacency.AdjacencyRuleSupport.isStandingSign` line 28: missed 1, covered 3
- `storagesign.adjacency.AdjacencyRuleSupport.isWallStandingSign` line 20: missed 1, covered 3
- `storagesign.adjacency.AdjacencyRuleSupport.nameOf` line 47: missed 1, covered 1

## storagesign/display

- `storagesign.display.NearbyStorageSignDisplay.refreshLabels` line 237: missed 8, covered 0
- `storagesign.display.NearbyStorageSignDisplay.monitorPlayers` line 86: missed 6, covered 12
- `storagesign.display.NearbyStorageSignDisplay.processAllocationPending` line 138: missed 6, covered 10
- `storagesign.display.NearbyStorageSignDisplay.processSearchQueue` line 122: missed 5, covered 9
- `storagesign.display.NearbyStorageSignDisplay.applyDesired` line 189: missed 4, covered 10
- `storagesign.display.NearbyStorageSignDisplay.hide` line 267: missed 3, covered 5
- `storagesign.display.NearbyStorageSignDisplay.removeLabel` line 276: missed 2, covered 4
- `storagesign.display.NearbyStorageSignDisplay.hasLineOfSight` line 179: missed 1, covered 11
- `storagesign.display.NearbyStorageSignDisplay.hide` line 263: missed 1, covered 1
- `storagesign.display.NearbyStorageSignDisplay.moved` line 291: missed 1, covered 7
- `storagesign.display.NearbyStorageSignDisplay.shutdown` line 60: missed 1, covered 3
- `storagesign.display.NearbyStorageSignDisplay.tick` line 71: missed 1, covered 3

## storagesign/index

- `storagesign.index.StorageSignIndex.lambda$rebuild$9` line 270: missed 10, covered 2
- `storagesign.index.StorageSignIndex.scanChunk` line 324: missed 4, covered 0
- `storagesign.index.StorageSignIndex.lambda$onChunkLoad$10` line 296: missed 2, covered 0
- `storagesign.index.StorageSignIndex.unregister` line 190: missed 2, covered 12
- `storagesign.index.StorageSignIndex.findByIdentifierExact` line 112: missed 1, covered 9
- `storagesign.index.StorageSignIndex.get` line 343: missed 1, covered 3
- `storagesign.index.StorageSignIndex.lambda$saveAsync$7` line 250: missed 1, covered 1
- `storagesign.index.StorageSignIndex.removeChunk` line 331: missed 1, covered 5
- `storagesign.index.StorageSignIndex.removeSecondary` line 359: missed 1, covered 3

## storagesign/item

- `storagesign.item.EnchantHelper.fromPrefix` line 75: missed 6, covered 12

## storagesign/listener

- `storagesign.listener.PlayerInteractListener.processStorageSignItemInteraction` line 150: missed 28, covered 26
- `storagesign.listener.PlayerInteractListener.onPlayerInteract` line 50: missed 12, covered 44
- `storagesign.listener.InventoryListener.onItemMove` line 74: missed 11, covered 19
- `storagesign.listener.InventoryListener.resolveAdjacentStorageSignForInventory` line 200: missed 10, covered 10
- `storagesign.listener.BlockEventListener.tryDropStorageSign` line 167: missed 6, covered 2
- `storagesign.listener.InventoryListener.onBlockDispense` line 158: missed 6, covered 8
- `storagesign.listener.InventoryListener.onInventoryPickup` line 127: missed 6, covered 12
- `storagesign.listener.BlockEventListener.onSignChange` line 102: missed 5, covered 11
- `storagesign.listener.PlayerInteractListener.importItems` line 233: missed 4, covered 18
- `storagesign.listener.BlockEventListener.onBlockPlace` line 73: missed 3, covered 5
- `storagesign.listener.EntityListener.autoCollectToHand` line 88: missed 2, covered 12
- `storagesign.listener.BlockEventListener.dropAttachedStorageSignsByAdjacency` line 159: missed 1, covered 1
- `storagesign.listener.BlockEventListener.dropSingleStorageSign` line 182: missed 1, covered 3
- `storagesign.listener.BlockEventListener.onBlockBreak` line 52: missed 1, covered 3
- `storagesign.listener.EntityListener.onEntityChangeBlock` line 125: missed 1, covered 5
- `storagesign.listener.EntityListener.onPlayerPickupItem` line 45: missed 1, covered 13

## storagesign/logging

- `storagesign.logging.ExternalLoggerBackend.log` line 33: missed 5, covered 3
- `storagesign.logging.ExternalLoggerBackend.<init>` line 14: missed 1, covered 1
- `storagesign.logging.ExternalLoggerBackend.close` line 49: missed 1, covered 1

## storagesign/task

- `storagesign.task.ExportSignTask.run` line 74: missed 9, covered 25
- `storagesign.task.ExportSignTask.addIntoSlot` line 210: missed 2, covered 6
- `storagesign.task.ExportSignTask.addToSource` line 168: missed 2, covered 8
- `storagesign.task.ExportSignTask.addToBrewing` line 186: missed 1, covered 9
- `storagesign.task.ExportSignTask.traceSkip` line 160: missed 1, covered 1
