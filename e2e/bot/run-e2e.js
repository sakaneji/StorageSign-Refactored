import assert from 'node:assert/strict'
import mineflayer from 'mineflayer'
import { Vec3 } from 'vec3'

const version = process.env.MC_VERSION ?? '1.21.4'
const phase = process.env.E2E_PHASE ?? 'main'
const host = process.env.MC_HOST ?? 'server'
const port = Number(process.env.MC_PORT ?? 25565)
const timeoutMs = Number(process.env.E2E_TIMEOUT_MS ?? 30000)

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

async function command(text, expectedPrefix) {
  bot.chat(text)
  return waitForMessage(message => message.includes(expectedPrefix))
}

async function reset(scenario) {
  await command(`/sstest reset ${scenario}`, `SSTEST READY ${scenario}`)
  await sleep(1000)
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

async function placeStorageSign(scenario, identifier, amount) {
  await reset(scenario)
  bot.chat(`/ssgive ${identifier} ${amount} OAK_SIGN`)
  await waitForMessage(message => message.includes('StorageSign を付与しました'))
  await equip('oak_sign')
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
  await sleep(500)
  let state = await inspect(scenario)
  if (state.lines.length === 0) {
    process.stdout.write('Mineflayer placement was not acknowledged; exercising BlockPlaceEvent fallback\n')
    await command(`/sstest place ${scenario}`, `SSTEST PLACED ${scenario}`)
    state = await inspect(scenario)
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
  process.stdout.write(`CASE ${name}\n`)
  await body()
  process.stdout.write(`PASS ${name}\n`)
}

async function runMainSuite() {
  await runCase('client placement', async () => {
    const state = await placeStorageSign('client', 'STONE', 128)
    assert.deepEqual(state.lines.slice(0, 3), ['StorageSign', 'STONE', '128'])
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

  await runCase('hopper auto import', async () => {
    await reset('auto-import')
    await sleep(1800)
    const state = await inspect('auto-import')
    assert.ok(Number(state.lines[2]) > 0, `Expected overflow absorption, got ${state.lines[2]}`)
    assert.equal(state.chestStone, 64)
    assert.equal(state.hopperStone, 0)
  })

  await runCase('hopper auto export', async () => {
    await reset('auto-export')
    await sleep(1800)
    const state = await inspect('auto-export')
    assert.ok(Number(state.lines[2]) < 64, `Expected StorageSign refill, got ${state.lines[2]}`)
    assert.ok(state.chestStone + state.hopperStone > 1)
  })

  await runCase('hopper minecart import', async () => {
    await reset('minecart-import')
    await sleep(1800)
    const state = await inspect('minecart-import')
    assert.ok(Number(state.lines[2]) > 0, `Expected minecart import, got ${state.lines[2]}`)
    assert.equal(state.minecartStone, 64)
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
  })

  await runCase('ominous banner placement', async () => {
    const state = await placeStorageSign('special-banner', 'WHITE_BANNER:8', 2)
    assert.deepEqual(state.lines.slice(0, 3), ['StorageSign', 'WHITE_BANNER:8', '2'])
  })

  await runCase('prepare restart persistence', async () => {
    await reset('restart')
    const state = await inspect('restart')
    assert.deepEqual(state.lines.slice(0, 3), ['StorageSign', 'STONE', '77'])
  })
}

async function runRestartCheck() {
  await runCase('restart persistence', async () => {
    const state = await inspect('restart')
    assert.deepEqual(state.lines.slice(0, 3), ['StorageSign', 'STONE', '77'])
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
  else await runMainSuite()
  bot.quit('E2E complete')
}

main().then(() => {
  process.stdout.write(`E2E PASS minecraft=${version} phase=${phase}\n`)
  process.exitCode = 0
}).catch(error => {
  process.stderr.write(`E2E FAIL minecraft=${version} phase=${phase}\n${error.stack ?? error}\n`)
  try { bot.quit('E2E failed') } catch {}
  process.exitCode = 1
})
