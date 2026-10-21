'use strict'
const assert = require('node:assert/strict')
const { createRequire } = require('node:module')
const path = require('node:path')
const load = createRequire(path.join(process.env.NATIVE_PLAYER_MODULES, 'package.json'))
const mineflayer = load('mineflayer')
const { Vec3 } = load('vec3')
const port = Number(process.env.NATIVE_PLAYER_PORT)
assert(Number.isInteger(port) && port > 1024 && port < 65536)
assert.equal(process.env.IE2_BREAK_PROBE, 'disposable')
const bot = mineflayer.createBot({ host: '127.0.0.1', port, username: 'IE2BreakProbe', auth: 'offline', version: '1.21.11' })
const seen = []
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))
let completed = false
bot.on('messagestr', message => { console.log('server:', message); seen.push(message) })
bot.on('error', error => { console.error(error); process.exitCode = 1 })
bot.on('kicked', reason => { if (!completed) { console.error(reason); process.exitCode = 1 } })
const deadline = setTimeout(() => { console.error('Native break client deadline'); process.exit(1) }, 150000)
async function until(test, description) {
  const limit = Date.now() + 30000
  while (Date.now() < limit) {
    if (seen.some(message => message === 'NATIVEBREAK:FAIL')) throw new Error('Server break ledger failed')
    if (test()) return
    await pause(50)
  }
  throw new Error('Timed out: ' + description)
}
async function run() {
  await new Promise((resolve, reject) => { bot.once('spawn', resolve); bot.once('error', reject) })
  await until(() => seen.includes('NATIVEBREAK:place-ready'), 'native break cases and exact shell')
  await until(() => bot.inventory.slots[36] != null && bot.inventory.slots[36].name !== 'netherite_pickaxe', 'real shell replaces previous tool in hotbar')
  await until(() => bot.entity.position.distanceTo(new Vec3(160.5, 100, 178.5)) < 1, 'placement teleport acknowledged')
  await bot.equip(bot.inventory.slots[36], 'hand')
  const support = new Vec3(160, 99, 176)
  await until(() => bot.blockAt(support) != null, 'placement support loaded')
  console.log('placement:', bot.heldItem.name, bot.entity.position, bot.blockAt(support).name)
  await bot.placeBlock(bot.blockAt(support), new Vec3(0, 1, 0))
  await pause(500)
  bot.chat('/breakfixture placed')
  await until(() => seen.includes('NATIVEBREAK:PASS'), 'native placed-shell ledger')
  completed = true
  console.log('PASS: actual Minecraft client placed the verified storage shell')
  clearTimeout(deadline)
  bot.quit('Native IE2 break fixture complete')
}
run().catch(error => { console.error(error); clearTimeout(deadline); bot.end(); process.exitCode = 1 })
