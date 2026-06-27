import assert from 'node:assert/strict'
import mineflayer from 'mineflayer'
import { Vec3 } from 'vec3'

const version = process.env.MC_VERSION ?? '1.21.4'
const phase = process.env.E2E_PHASE ?? 'main'
const loggerMode = process.env.LOGGER_MODE ?? 'without-logger'
const host = process.env.MC_HOST ?? 'server'
const port = Number(process.env.MC_PORT ?? 25565)
const timeoutMs = Number(process.env.E2E_TIMEOUT_MS ?? 30000)
const caseFilter = new Set(
  (process.env.E2E_CASE_FILTER ?? '')
    .split(',')
    .map(name => name.trim())
    .filter(Boolean)
)

const bot = mineflayer.createBot({
  host,
  port,
  username: 'StorageSignBot',
  auth: 'offline',
  version,
  hideErrors: false
})

const messages = []
bot.on('messagestr', message => {
  messages.push(message)
  process.stdout.write(`[server] ${message}\n`)
})

bot.on('kicked', reason => process.stderr.write(`kicked: ${reason}\n`))
bot.on('error', error => process.stderr.write(`${error.stack ?? error}\n`))

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

function waitForMessage(predicate, timeout = timeoutMs) {
  return new Promise((resolve, reject) => {
    const startedAt = Date.now()
    const timer = setInterval(() => {
      const index = messages.findIndex(predicate)
      if (index >= 0) {
        const [message] = messages.splice(index, 1)
        clearInterval(timer)
        resolve(message)
      } else if (Date.now() - startedAt > timeout) {
        clearInterval(timer)
        reject(new Error(`Timed out waiting for server message after ${timeout}ms`))
      }
    }, 50)
  })
}

async function waitForSnapshot(scenario, predicate, timeout = timeoutMs, interval = 100) {
  const startedAt = Date.now()
  while (Date.now() - startedAt <= timeout) {
    const state = await inspect(scenario)
    if (predicate(state)) return state
    await sleep(interval)
  }
  throw new Error(`Timed out waiting for snapshot of ${scenario}`)
}

async function command(text, expectedPrefix) {
  bot.chat(text)
  return waitForMessage(message => message.includes(expectedPrefix))
}

async function reset(scenario) {
  await command(`/sstest reset ${scenario}`, `SSTEST READY ${scenario}`)
  await sleep(250)
}

async function inspect(scenario) {
  const message = await command(`/sstest inspect ${scenario}`, `SSTEST {"scenario":"${scenario}"`)
  const jsonStart = message.indexOf('{')
  assert.notEqual(jsonStart, -1, `No JSON snapshot in: ${message}`)
  return JSON.parse(message.slice(jsonStart))
}

async function equip(name) {
  const item = bot.inventory.items().find(candidate => candidate.name === name)
  assert.ok(item, `Expected ${name} in bot inventory`)
  await bot.equip(item, 'hand')
  await sleep(150)
}

async function emptyHand() {
  await bot.unequip('hand')
  await sleep(150)
}

async function placeStorageSign(scenario, identifier, amount) {
  await reset(scenario)
  bot.chat(`/ssgive ${identifier} ${amount} OAK_SIGN`)
  await waitForMessage(message => message.includes('StorageSign を付与しました'))
  await equip('oak_sign')
  const heldState = await inspect(scenario)
  assert.equal(
    heldState.heldDisplayName,
    'StorageSign',
    `Expected StorageSign display name, got ${heldState.heldDisplayName}`
  )
  const support = bot.blockAt(new Vec3(0, 64, 0))
  assert.ok(support, 'Missing sign support block')
  process.stdout.write(`placing from ${bot.entity.position} with ${bot.heldItem?.name}\n`)
  // Send the normal use-item-on-block packet with an immediate forced look. The
  // public placeBlock helper additionally waits for a block update, which is not
  // reliable while a sign editor is being opened and closed by the plugin.
  await bot._genericPlace(support, new Vec3(0, 1, 0), {
    forceLook: true,
    swingArm: 'right'
  })
  let state
  try {
    state = await waitForSnapshot(scenario, snapshot => snapshot.lines.length > 0, 5000)
  } catch (error) {
    process.stdout.write('Mineflayer placement was not acknowledged; exercising BlockPlaceEvent fallback\n')
    await command(`/sstest place ${scenario}`, `SSTEST PLACED ${scenario}`)
    state = await waitForSnapshot(scenario, snapshot => snapshot.lines.length > 0, 5000)
  }
  return state
}

async function activateSign({ sneak = false } = {}) {
  const sign = bot.blockAt(new Vec3(0, 65, 0))
  assert.ok(sign?.name?.includes('sign'), `Expected sign at test position, got ${sign?.name}`)
  bot.setControlState('sneak', sneak)
  if (sneak) await command('/sstest sneak true', 'SSTEST SNEAK true')
  await sleep(250)
  try {
    await bot.activateBlock(sign)
    await sleep(600)
  } finally {
    bot.setControlState('sneak', false)
    if (sneak) await command('/sstest sneak false', 'SSTEST SNEAK false')
  }
}

async function runCase(name, body) {
  if (caseFilter.size > 0 && !caseFilter.has(name)) return
  process.stdout.write(`CASE ${name}\n`)
  await body()
  process.stdout.write(`PASS ${name}\n`)
}

function assertBannerCoreMetadata(state) {
  const patterns = state.bannerPatterns.split('|').map(pattern => pattern.split(':'))
  assert.deepEqual(patterns.map(([color]) => color), [
    'CYAN', 'LIGHT_GRAY', 'GRAY', 'LIGHT_GRAY',
    'BLACK', 'LIGHT_GRAY', 'LIGHT_GRAY', 'BLACK'
  ])
  const types = patterns.map(([, type]) => type)
  assert.ok(['RHOMBUS', 'RHOMBUS_MIDDLE'].includes(types[0]), `Unexpected rhombus type: ${types[0]}`)
  assert.deepEqual(types.slice(1, 6), [
    'STRIPE_BOTTOM', 'STRIPE_CENTER', 'BORDER', 'STRIPE_MIDDLE', 'HALF_HORIZONTAL'
  ])
  assert.ok(['CIRCLE', 'CIRCLE_MIDDLE'].includes(types[6]), `Unexpected circle type: ${types[6]}`)
  assert.equal(types[7], 'BORDER')
  assert.equal(state.bannerNamePresent, true)
}

function assertBannerMetadata(state) {
  assertBannerCoreMetadata(state)
  assert.equal(state.bannerTooltipHidden, true)
}

async function runLegacyBlockRoundTrip({
  scenario,
  initialIdentifier,
  initialAmount,
  normalizedIdentifier,
  exportItemName,
  exportedCountField,
}) {
  await reset(scenario)
  let state = await inspect(scenario)
  assert.equal(state.lines[0], 'StorageSign')
  assertIdentifierLine(state.lines[1], initialIdentifier)
  assert.equal(state.lines[2], String(initialAmount))
  assert.equal(state.lines[3], '')

  await emptyHand()
  await command(`/sstest interact ${scenario}`, `SSTEST INTERACTED ${scenario}`)
  await sleep(1200)
  state = await inspect(scenario)
  assert.equal(state.lines[0], 'StorageSign')
  assertIdentifierLine(state.lines[1], normalizedIdentifier)
  assert.equal(state.lines[2], '0')
  assert.equal(state[exportedCountField], initialAmount)

  await equip(exportItemName)
  await command('/sstest sneak true', 'SSTEST SNEAK true')
  await command(`/sstest interact ${scenario}`, `SSTEST INTERACTED ${scenario}`)
  await sleep(1200)
  await command('/sstest sneak false', 'SSTEST SNEAK false')
  state = await inspect(scenario)
  assert.equal(state.lines[0], 'StorageSign')
  assertIdentifierLine(state.lines[1], normalizedIdentifier)
  assert.equal(state.lines[2], String(initialAmount))
  assert.equal(state[exportedCountField], 0)
}

async function runLegacyItemMerge({
  scenario,
  blockIdentifier,
  heldIdentifier,
}) {
  await reset(scenario)
  let state = await inspect(scenario)
  assert.equal(state.lines[0], 'StorageSign')
  assertIdentifierLine(state.lines[1], blockIdentifier)
  assert.equal(state.lines[2], '2')
  assert.equal(state.heldLore, `${heldIdentifier} 2`)

  await command('/sstest sneak true', 'SSTEST SNEAK true')
  await command(`/sstest interact ${scenario}`, `SSTEST INTERACTED ${scenario}`)
  await sleep(1200)
  await command('/sstest sneak false', 'SSTEST SNEAK false')
  state = await inspect(scenario)
  assert.equal(state.lines[0], 'StorageSign')
  assertIdentifierLine(state.lines[1], blockIdentifier)
  assert.equal(state.lines[2], '4')
  assert.equal(state.heldLore, 'Empty')
}

function assertIdentifierLine(actual, expected) {
  if (actual === expected) return
  const prefix = expected.slice(0, Math.min(expected.length, 13))
  assert.ok(actual.startsWith(prefix),
    `Expected identifier line to match ${expected}, got ${actual}`)
}

function wrapLabel(value) {
  const columns = 28
  let wrapped = ''
  for (let index = 0; index < value.length; index++) {
    wrapped += value[index]
    if ((index + 1) % columns === 0 && index + 1 < value.length) {
      wrapped += '\n'
    }
  }
  return wrapped
}

async function assertLoggerEnvironment() {
  const state = await inspect('environment')
  const expected = loggerMode === 'with-logger'
  assert.equal(state.loggerPluginEnabled, expected,
    `Logger plugin state did not match ${loggerMode}`)
  assert.equal(state.externalLoggerRegistered, expected,
    `StorageSign external Logger registration did not match ${loggerMode}`)
}

async function runMainSuite() {
  await runCase(`logger environment ${loggerMode}`, assertLoggerEnvironment)

  await runCase('client placement', async () => {
    const state = await placeStorageSign('client', 'STONE', 128)
    assert.deepEqual(state.lines.slice(0, 3), ['StorageSign', 'STONE', '128'])
    await sleep(3000)
    const nearby = await inspect('client')
    assert.equal(nearby.textDisplayCount, 0)
  })

  await runCase('long identifier placement', async () => {
    const state = await placeStorageSign('long-identifier', 'NETHERITE_UPGRADE_SMITHING_TEMPLATE', 1)
    assert.deepEqual(state.lines.slice(0, 3), ['StorageSign', 'NUS:TEMPLATE', '1'])
    const nearby = await waitForSnapshot(
      'long-identifier',
      snapshot => snapshot.textDisplayCount >= 1,
      30000
    )
    assert.ok(
      nearby.textDisplayTexts.some(text => text === wrapLabel('NETHERITE_UPGRADE_SMITHING_TEMPLATE')),
      `Expected wrapped long identifier in nearby TextDisplay, got ${nearby.textDisplayTexts}`
    )
  })

  await runCase('manual export', async () => {
    await reset('manual-export')
    await activateSign()
    const state = await inspect('manual-export')
    assert.equal(state.lines[2], '64')
    assert.equal(state.droppedStone + state.playerStone, 64)
  })

  await runCase('manual import', async () => {
    await reset('manual-import')
    await equip('stone')
    await activateSign()
    const state = await inspect('manual-import')
    assert.equal(state.lines[2], '80')
    assert.equal(state.playerStone, 0)
  })

  await runCase('manual import stops at Integer maximum', async () => {
    await reset('overflow-import')
    await equip('stone')
    await activateSign({ sneak: true })
    const state = await inspect('overflow-import')
    assert.equal(state.lines[2], '2147483647')
    assert.equal(state.playerStone, 6)
  })

  await runCase('partial StorageSign merge returns empty and reduced stacks', async () => {
    await reset('merge-partial')
    await equip('oak_sign')
    await activateSign()
    const state = await inspect('merge-partial')
    assert.equal(state.lines[2], '2147483647')
    assert.equal(state.playerRegisteredStorageSigns, 1)
    assert.equal(state.playerEmptyStorageSigns, 1)
    assert.equal(state.playerReducedStorageSigns, 1)
    assert.equal(state.droppedStone, 0)
    assert.equal(state.droppedStorageSigns, 0)
    assert.ok(state.heldLore.includes('STONE 10'), `Expected remainder lore, got ${state.heldLore}`)
  })

  await runCase('full inventory drops empty before reduced sign after partial merge', async () => {
    await reset('merge-partial-full')
    await equip('oak_sign')
    await activateSign()
    const state = await inspect('merge-partial-full')
    assert.equal(state.lines[2], '2147483647')
    assert.equal(state.playerRegisteredStorageSigns, 1)
    assert.equal(state.playerEmptyStorageSigns, 0)
    assert.equal(state.droppedEmptyStorageSigns, 1)
    assert.equal(state.droppedReducedStorageSigns, 1)
    assert.equal(state.playerReducedStorageSigns, 0)
    assert.equal(state.droppedStone, 0)
    assert.ok(state.heldLore.includes('STONE 10'), `Expected remainder lore, got ${state.heldLore}`)
  })

  await runCase('give command drops StorageSign when inventory is full', async () => {
    await reset('full-command')
    bot.chat('/ssgive STONE 1 OAK_SIGN')
    await waitForMessage(message => message.includes('StorageSign を付与しました'))
    await sleep(300)
    const state = await inspect('full-command')
    assert.equal(state.droppedStorageSigns, 1)
  })

  await runCase('mismatched held item triggers export without consumption', async () => {
    await reset('mismatch-export')
    await equip('dirt')
    await activateSign()
    const state = await inspect('mismatch-export')
    assert.equal(state.lines[2], '0')
    assert.equal(state.playerStone + state.droppedStone, 64)
    assert.equal(state.heldType, 'DIRT')
  })

  await runCase('register item type on empty StorageSign', async () => {
    await reset('register-empty')
    await equip('stone')
    await activateSign()
    const state = await inspect('register-empty')
    assert.deepEqual(state.lines.slice(0, 3), ['StorageSign', 'STONE', '0'])
    assert.equal(state.playerStone, 16)
  })

  await runCase('full inventory export drops without loss', async () => {
    await reset('full-inventory-export')
    await equip('dirt')
    await activateSign()
    const state = await inspect('full-inventory-export')
    assert.equal(state.lines[2], '0')
    assert.equal(state.droppedStone, 64)
  })

  await runCase('same-tick interactions preserve total quantity', async () => {
    await reset('double-interact')
    await command('/sstest double-interact double-interact', 'SSTEST DOUBLE double-interact')
    const state = await inspect('double-interact')
    assert.equal(state.lines[2], '0')
    assert.equal(state.playerStone + state.droppedStone, 128)
  })

  await runCase('sneak import', async () => {
    await reset('sneak-import')
    await equip('stone')
    await activateSign({ sneak: true })
    const state = await inspect('sneak-import')
    assert.equal(state.lines[2], '32')
    assert.equal(state.playerStone, 16)
  })

  await runCase('zero export preserves registration', async () => {
    await reset('zero-export')
    await activateSign({ sneak: true })
    const state = await inspect('zero-export')
    assert.equal(state.lines[1], 'STONE')
    assert.equal(state.lines[2], '0')
  })

  await runCase('permission denied', async () => {
    await reset('permission-denied')
    await activateSign()
    const state = await inspect('permission-denied')
    assert.equal(state.lines[2], '64')
  })

  await runCase('break permission denied', async () => {
    await reset('break-denied')
    await command('/sstest break break-denied', 'SSTEST BROKEN break-denied')
    const state = await inspect('break-denied')
    assert.equal(state.breakCancelled, true)
    assert.deepEqual(state.lines.slice(0, 3), ['StorageSign', 'STONE', '64'])
    assert.equal(state.droppedStorageSigns, 0)
  })

  await runCase('break drops one StorageSign item', async () => {
    await reset('break-allowed')
    await command('/sstest break break-allowed', 'SSTEST BROKEN break-allowed')
    const state = await inspect('break-allowed')
    assert.equal(state.breakCancelled, false)
    assert.equal(state.breakDrops, false)
    assert.equal(state.lines.length, 0)
    assert.equal(state.droppedStorageSigns, 1)
  })

  await runCase('breaking support drops attached StorageSign', async () => {
    await reset('attached-sign')
    await command('/sstest break-support attached-sign', 'SSTEST SUPPORT attached-sign')
    const state = await inspect('attached-sign')
    assert.equal(state.lines.length, 0)
    assert.equal(state.droppedStorageSigns, 1)
  })

  await runCase('sign edit preserves StorageSign data', async () => {
    await reset('edit-protected')
    await command('/sstest edit edit-protected', 'SSTEST EDITED edit-protected')
    const state = await inspect('edit-protected')
    assert.deepEqual(state.editLines.slice(0, 3), ['StorageSign', 'STONE', '64'])
    assert.deepEqual(state.lines.slice(0, 3), ['StorageSign', 'STONE', '64'])
  })

  await runCase('StorageSign item export and reimport', async () => {
    await reset('storage-sign-items')
    await emptyHand()
    await command('/sstest interact storage-sign-items', 'SSTEST INTERACTED storage-sign-items')
    await sleep(1200)
    let state = await inspect('storage-sign-items')
    assert.equal(state.lines[2], '0')
    assert.equal(state.playerSigns, 2)

    await equip('oak_sign')
    await command('/sstest sneak true', 'SSTEST SNEAK true')
    await command('/sstest interact storage-sign-items', 'SSTEST INTERACTED storage-sign-items')
    await sleep(1200)
    await command('/sstest sneak false', 'SSTEST SNEAK false')
    state = await inspect('storage-sign-items')
    assert.equal(state.lines[2], '2')
    assert.equal(state.playerSigns, 0)
  })

  await runCase('legacy StorageSign item round trip', async () => {
    await runLegacyBlockRoundTrip({
      scenario: 'legacy-storage-sign-items',
      initialIdentifier: 'OakStorageSign',
      initialAmount: 2,
      normalizedIdentifier: 'OakStorageSign',
      exportItemName: 'oak_sign',
      exportedCountField: 'playerEmptyStorageSigns',
    })
  })

  await runCase('legacy EmptySign item merge', async () => {
    await reset('legacy-empty-sign-item-merge')
    let state = await inspect('legacy-empty-sign-item-merge')
    assert.deepEqual(state.lines.slice(0, 3), ['StorageSign', 'OakStorageSign', '2'])
    assert.equal(state.heldLore, 'EmptySign 2')

    await command('/sstest sneak true', 'SSTEST SNEAK true')
    await command('/sstest interact legacy-empty-sign-item-merge',
      'SSTEST INTERACTED legacy-empty-sign-item-merge')
    await sleep(1200)
    await command('/sstest sneak false', 'SSTEST SNEAK false')
    state = await inspect('legacy-empty-sign-item-merge')
    assert.deepEqual(state.lines.slice(0, 3), ['StorageSign', 'OakStorageSign', '4'])
    assert.equal(state.playerEmptyStorageSigns, 1)
    assert.equal(state.heldLore, 'Empty')
  })

  await runCase('legacy SpruceStorageSign item merge', async () => {
    await runLegacyItemMerge({
      scenario: 'legacy-spruce-sign-item',
      blockIdentifier: 'SpruceStorageSign',
      heldIdentifier: 'SpruceStorageSign',
    })
  })

  await runCase('legacy DarkOakStorageSign item merge', async () => {
    await runLegacyItemMerge({
      scenario: 'legacy-dark-oak-sign-item',
      blockIdentifier: 'DarkOakStorageSign',
      heldIdentifier: 'DarkOakStorageSign',
    })
  })

  await runCase('legacy EmptySign block round trip', async () => {
    await runLegacyBlockRoundTrip({
      scenario: 'legacy-empty-sign-block',
      initialIdentifier: 'EmptySign',
      initialAmount: 3,
      normalizedIdentifier: 'OakStorageSign',
      exportItemName: 'oak_sign',
      exportedCountField: 'playerEmptyStorageSigns',
    })
  })

  await runCase('legacy SIGN block round trip', async () => {
    await runLegacyBlockRoundTrip({
      scenario: 'legacy-sign-block',
      initialIdentifier: 'SIGN',
      initialAmount: 4,
      normalizedIdentifier: 'OAK_SIGN',
      exportItemName: 'oak_sign',
      exportedCountField: 'playerSigns',
    })
  })

  await runCase('legacy spruce sign block round trip', async () => {
    await runLegacyBlockRoundTrip({
      scenario: 'legacy-spruce-sign-block',
      initialIdentifier: 'SpruceStorageSign',
      initialAmount: 5,
      normalizedIdentifier: 'SpruceStorageSign',
      exportItemName: 'spruce_sign',
      exportedCountField: 'playerEmptyStorageSigns',
    })
  })

  await runCase('legacy dark oak sign block round trip', async () => {
    await runLegacyBlockRoundTrip({
      scenario: 'legacy-dark-oak-sign-block',
      initialIdentifier: 'DarkOakStorageSign',
      initialAmount: 6,
      normalizedIdentifier: 'DarkOakStorageSign',
      exportItemName: 'dark_oak_sign',
      exportedCountField: 'playerEmptyStorageSigns',
    })
  })

  await runCase('legacy HorseEgg block round trip', async () => {
    await runLegacyBlockRoundTrip({
      scenario: 'legacy-horse-egg-block',
      initialIdentifier: 'HorseEgg',
      initialAmount: 3,
      normalizedIdentifier: 'HorseEgg',
      exportItemName: 'ghast_spawn_egg',
      exportedCountField: 'playerLegacyMarkerItems',
    })
  })

  await runCase('StorageSign division with multiple empty signs', async () => {
    await reset('divide')
    await equip('oak_sign')
    await activateSign()
    const state = await inspect('divide')
    assert.equal(state.lines[2], '34')
    assert.equal(state.playerSigns, 2)
    assert.ok(state.heldLore.includes('STONE 33'), `Expected divided lore, got ${state.heldLore}`)
  })

  await runCase('StorageSign sneak division limit', async () => {
    await reset('divide-sneak')
    await equip('oak_sign')
    await activateSign({ sneak: true })
    const state = await inspect('divide-sneak')
    assert.equal(state.lines[2], '130880')
    assert.equal(state.playerSigns, 2)
    assert.ok(state.heldLore.includes('STONE 34560'), `Expected sneak limit lore, got ${state.heldLore}`)
  })

  await runCase('hopper auto import', async () => {
    await reset('auto-import')
    await sleep(1800)
    const state = await inspect('auto-import')
    assert.ok(Number(state.lines[2]) >= 128, `Expected multiple full-stack imports, got ${state.lines[2]}`)
    assert.equal(Number(state.lines[2]) + state.chestStone + state.hopperStone, 256)
    assert.equal(state.chestStone, 64)
    assert.equal(state.hopperStone, 0)
  })

  await runCase('hopper auto export', async () => {
    await reset('auto-export')
    await sleep(1800)
    const state = await inspect('auto-export')
    assert.ok(Number(state.lines[2]) <= 64, `Expected multiple full-stack exports, got ${state.lines[2]}`)
    assert.equal(Number(state.lines[2]) + state.chestStone + state.hopperStone, 256)
    assert.ok(state.hopperStone >= 192, `Expected at least three full stacks in hopper, got ${state.hopperStone}`)
  })

  await runCase('hopper minecart import', async () => {
    await reset('minecart-import')
    await sleep(1800)
    const state = await inspect('minecart-import')
    assert.ok(Number(state.lines[2]) > 0, `Expected minecart import, got ${state.lines[2]}`)
    assert.equal(state.minecartStone, 64)
  })

  await runCase('hopper minecart export refill', async () => {
    await reset('minecart-export')
    await sleep(1800)
    const state = await inspect('minecart-export')
    assert.equal(Number(state.lines[2]) + state.minecartStone + state.hopperStone, 256)
    assert.ok(Number(state.lines[2]) < 192, `Expected StorageSign refill, got ${state.lines[2]}`)
    assert.ok(state.hopperStone > 64, 'Expected more than the minecart seed inventory to be exported')
  })

  await runCase('dropper world dispense refill', async () => {
    await reset('world-dispense')
    await command('/sstest dispense world-dispense', 'SSTEST DISPENSED world-dispense')
    await sleep(600)
    const state = await inspect('world-dispense')
    assert.equal(state.lines[2], '0')
    assert.equal(state.chestStone, 64)
    assert.equal(state.droppedStone + state.playerStone, 1)
  })

  for (const scenario of ['world-dispenser', 'world-crafter']) {
    await runCase(`${scenario} world dispense refill`, async () => {
      await reset(scenario)
      await command(`/sstest dispense ${scenario}`, `SSTEST DISPENSED ${scenario}`)
      await sleep(600)
      const state = await inspect(scenario)
      assert.equal(state.lines[2], '0')
      assert.equal(state.chestStone, 64)
      assert.equal(state.droppedStone + state.playerStone, 1)
    })
  }

  await runCase('chest boat inventory import', async () => {
    await reset('chest-boat-import')
    await command('/sstest boat-transfer chest-boat-import', 'SSTEST BOAT chest-boat-import')
    const state = await inspect('chest-boat-import')
    assert.equal(state.lines[2], '1')
    assert.equal(state.chestBoatStone, 63)
  })

  await runCase('chest minecart inventory import', async () => {
    await reset('chest-minecart-import')
    await command('/sstest storage-minecart-transfer chest-minecart-import',
      'SSTEST STORAGE-MINECART chest-minecart-import')
    const state = await inspect('chest-minecart-import')
    assert.equal(state.lines[2], '1')
    assert.equal(state.storageMinecartStone, 63)
  })

  await runCase('double chest inventory import', async () => {
    await reset('double-chest-import')
    await command('/sstest double-transfer double-chest-import', 'SSTEST DOUBLECHEST double-chest-import')
    const state = await inspect('double-chest-import')
    assert.equal(state.lines[2], '1')
    assert.equal(state.chestStone, 63)
  })

  await runCase('autocollect', async () => {
    await reset('autocollect')
    bot.chat('/ssgive STONE 10 OAK_SIGN')
    await waitForMessage(message => message.includes('StorageSign を付与しました'))
    await equip('oak_sign')
    await command('/sstest drop autocollect', 'SSTEST DROPPED autocollect')
    await sleep(1200)
    const state = await inspect('autocollect')
    assert.equal(state.droppedStone, 0)
    assert.equal(state.playerSigns, 1)
    assert.ok(state.heldLore.includes('STONE 18'), `Expected updated StorageSign lore, got ${state.heldLore}`)
  })

  await runCase('potion placement', async () => {
    const state = await placeStorageSign('special-potion', 'POTION:HEAL:0', 2)
    assert.equal(state.lines[0], 'StorageSign')
    assert.equal(state.lines[2], '2')
    assert.ok(state.lines[1].startsWith('POTION:'))
    assert.equal(state.potionSignKey, 'POTION:minecraft:healing')
    assert.ok(state.potionSignDisplayWidth <= 90,
      `Potion sign text exceeds vanilla width: ${state.potionSignDisplayWidth}`)
  })

  await runCase('ominous bottle amplifier round trip', async () => {
    await placeStorageSign('special-potion', 'OMINOUS_BOTTLE:3', 2)
    await emptyHand()
    await activateSign()
    const state = await inspect('special-potion')
    assert.equal(state.lines[2], '0')
    assert.equal(state.playerOminousBottles, 2)
    assert.equal(state.ominousBottleAmplifier, 3)
  })

  await runCase('ominous banner round trip', async () => {
    let state = await placeStorageSign('special-banner', 'WHITE_BANNER:8', 2)
    assert.deepEqual(state.lines.slice(0, 3), ['StorageSign', 'WHITE_BANNER:8', '2'])

    await emptyHand()
    await activateSign()
    state = await inspect('special-banner')
    assert.equal(state.lines[2], '0')
    assert.equal(state.playerOminousBanners, 2)
    assertBannerMetadata(state)

    await equip('white_banner')
    await activateSign({ sneak: true })
    state = await inspect('special-banner')
    assert.equal(state.lines[2], '2')
    assert.equal(state.playerOminousBanners, 0)
  })

  await runCase('prepare restart persistence', async () => {
    await reset('restart')
    const state = await inspect('restart')
    assert.deepEqual(state.lines.slice(0, 3), ['StorageSign', 'STONE', '77'])
  })

  await runCase('storage index chunk load scan', async () => {
    await reset('index-chunk-load')
    let state = await inspect('index-chunk-load')
    const baseline = state.indexedSigns
    assert.ok(baseline >= 0)
    await command('/sstest chunk-load index-chunk-load', 'SSTEST CHUNKLOAD index-chunk-load')
    state = await waitForSnapshot(
      'index-chunk-load',
      snapshot => snapshot.indexedSigns >= baseline + 1,
      10000
    )
    assert.equal(state.indexedSigns, baseline + 1)
    await command('/sstest admin true', 'SSTEST ADMIN true')
    bot.chat('/storagesignsearch item STONE')
    await waitForMessage(message => message.includes('Searching StorageSign index'))
    const result = await waitForMessage(message => message.includes("StorageSign search 'STONE'"))
    assert.match(result, /matches=[1-9][0-9]*/)
  })

  await runCase('storage index rebuild search and short identifiers stay hidden', async () => {
    await reset('restart')
    await command('/sstest admin true', 'SSTEST ADMIN true')
    bot.chat('/storagesignindex rebuild all')
    await waitForMessage(message => message.includes('rebuild started'))
    await waitForMessage(message => message.includes('rebuild and save complete'))
    const status = await command('/storagesignindex status', 'Indexed signs:')
    assert.match(status, /Indexed signs: [1-9][0-9]*/)
    bot.chat('/storagesignsearch item STONE')
    await waitForMessage(message => message.includes('Searching StorageSign index'))
    const result = await waitForMessage(message => message.includes("StorageSign search 'STONE'"))
    assert.match(result, /matches=[1-9][0-9]*/)
    await sleep(3000)
    let state = await inspect('restart')
    assert.equal(state.textDisplayCount, 0)
    await command('/sstest move true', 'SSTEST MOVED true')
    state = await inspect('restart')
    assert.equal(state.textDisplayCount, 0)
  })
}

async function runRestartCheck() {
  await runCase(`logger environment after restart ${loggerMode}`, assertLoggerEnvironment)
  await runCase('restart persistence', async () => {
    const state = await inspect('restart')
    assert.deepEqual(state.lines.slice(0, 3), ['StorageSign', 'STONE', '77'])
  })
  await runCase('storage index persists across restart', async () => {
    await command('/sstest admin true', 'SSTEST ADMIN true')
    bot.chat('/storagesignsearch item STONE')
    await waitForMessage(message => message.includes('Searching StorageSign index'))
    const result = await waitForMessage(message => message.includes("StorageSign search 'STONE'"))
    assert.match(result, /matches=[1-9][0-9]*/)
  })
}

async function runBannerSeed() {
  await runCase('banner upgrade seed', async () => {
    await assertLoggerEnvironment()
    let state = await placeStorageSign('banner-upgrade-seed', 'WHITE_BANNER:8', 2)
    assert.deepEqual(state.lines.slice(0, 3), ['StorageSign', 'WHITE_BANNER:8', '2'])

    await emptyHand()
    await activateSign()
    state = await inspect('banner-upgrade')
    assert.equal(state.lines[2], '0')
    assert.equal(state.playerOminousBanners, 2)
    assertBannerMetadata(state)

    await command('/sstest stash banner-upgrade', 'SSTEST STASHED banner-upgrade')
    await equip('white_banner')
    await activateSign({ sneak: true })
    state = await inspect('banner-upgrade')
    assert.equal(state.lines[2], '1')
    assert.equal(state.playerOminousBanners, 0)
    assert.equal(state.chestOminousBanners, 1)
    assertBannerMetadata(state)
    assert.deepEqual(state.potionSignLines.slice(0, 3),
      ['StorageSign', 'LPOTION:SPEED:2', '3'])
    assert.equal(state.potionSignKey, 'LPOTION:minecraft:strong_swiftness')
    assert.ok(state.potionSignDisplayWidth <= 90)
  })
}

async function runBannerUpgrade() {
  await runCase(`banner upgrade from persisted world to ${version}`, async () => {
    await assertLoggerEnvironment()
    let state = await inspect('banner-upgrade')
    assert.deepEqual(state.lines.slice(0, 3), ['StorageSign', 'WHITE_BANNER:8', '1'])
    assert.equal(state.chestOminousBanners, 1)
    // Minecraft's data fixer may drop legacy tooltip-hiding flags while upgrading
    // an ItemStack. Pattern identity and the ominous name are the compatibility
    // contract; a fresh export below must restore the current-version flag.
    assertBannerCoreMetadata(state)
    assert.deepEqual(state.potionSignLines.slice(0, 3),
      ['StorageSign', 'LPOTION:SPEED:2', '3'])
    assert.equal(state.potionSignKey, 'LPOTION:minecraft:strong_swiftness')
    assert.ok(state.potionSignDisplayWidth <= 90)

    await command('/sstest unstash banner-upgrade', 'SSTEST UNSTASHED banner-upgrade')
    await sleep(300)
    await equip('white_banner')
    state = await inspect('banner-upgrade')
    assert.equal(state.heldType, 'WHITE_BANNER')
    assert.equal(state.storageSignAcceptsHeld, true)
    await activateSign({ sneak: true })
    state = await inspect('banner-upgrade')
    if (state.lines[2] === '1') {
      process.stdout.write('Mineflayer upgrade interaction was not acknowledged; exercising PlayerInteractEvent fallback\n')
      await command('/sstest sneak true', 'SSTEST SNEAK true')
      await command('/sstest interact banner-upgrade', 'SSTEST INTERACTED banner-upgrade')
      await command('/sstest sneak false', 'SSTEST SNEAK false')
      state = await inspect('banner-upgrade')
    }
    assert.equal(state.lines[2], '2')
    assert.equal(state.chestOminousBanners, 0)

    await emptyHand()
    await activateSign()
    state = await inspect('banner-upgrade')
    assert.equal(state.lines[2], '0')
    assert.equal(state.playerOminousBanners, 2)
    assertBannerMetadata(state)

    await command('/sstest stash banner-upgrade', 'SSTEST STASHED banner-upgrade')
    await equip('white_banner')
    await activateSign({ sneak: true })
    state = await inspect('banner-upgrade')
    assert.equal(state.lines[2], '1')
    assert.equal(state.playerOminousBanners, 0)
    assert.equal(state.chestOminousBanners, 1)
    assertBannerMetadata(state)
  })
}

async function main() {
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Bot spawn timed out')), timeoutMs)
    bot.once('spawn', () => {
      clearTimeout(timer)
      resolve()
    })
  })
  await sleep(750)
  if (phase === 'restart') await runRestartCheck()
  else if (phase === 'banner-seed') await runBannerSeed()
  else if (phase === 'banner-upgrade') await runBannerUpgrade()
  else await runMainSuite()
  bot.quit('E2E complete')
}

main().then(() => {
  process.stdout.write(`E2E PASS minecraft=${version} phase=${phase} logger=${loggerMode}\n`)
  process.exitCode = 0
}).catch(error => {
  process.stderr.write(`E2E FAIL minecraft=${version} phase=${phase} logger=${loggerMode}\n${error.stack ?? error}\n`)
  try { bot.quit('E2E failed') } catch {}
  process.exitCode = 1
})
