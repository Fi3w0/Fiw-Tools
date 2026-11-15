// Plays the QA pack (examples/qa) against a production jar through the vanilla wire protocol.
// stdout is a JSON bridge to the runner: {"console": ...} lines become server console input.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { once } = require('node:events')
const mineflayer = require('mineflayer')

const [port, version, phase, directory] = process.argv.slice(2)
const name = 'FiwQA'
const qa = path.join(__dirname, '..', '..', 'examples', 'qa')
const emit = value => process.stdout.write(JSON.stringify(value) + '\n')
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))
const deadline = setTimeout(() => { emit({ error: 'Player verification timed out' }); process.exit(1) }, 150000)
const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, auth: 'offline', version })
const messages = []
bot.on('messagestr', message => messages.push(message.replace(/§./g, '')))
bot.on('error', error => { emit({ error: error.stack }); process.exit(1) })
bot.on('kicked', reason => { emit({ error: 'Client kicked', reason }); process.exit(1) })

async function until(condition, description, timeout = 12000) {
  const end = Date.now() + timeout
  while (!condition()) {
    if (Date.now() > end) throw new Error(`Timed out: ${description}; ${messages.slice(-12).join('; ')}`)
    await delay(100)
  }
}
// mineflayer waits for a 5x5 chunk square with a fixed 10 s limit. A slow server start or a
// spawn on a chunk border must not fail the run once the client's own chunk has arrived.
async function chunksLoaded() {
  for (let attempt = 0; attempt < 6; attempt++) {
    try { return await bot.waitForChunksToLoad() } catch (error) {
      if (bot.world.getColumnAt(bot.entity.position)) return
    }
  }
  throw new Error('Client chunks never loaded')
}
async function consoleCommand(command) { emit({ console: command }); await delay(300) }
async function chat(command, response) {
  const start = messages.length
  bot.chat(command)
  await until(() => messages.slice(start).some(m => m.includes(response)), `${command} -> ${response}`)
}
// Builds test terrain only after the server confirms it. Chunks away from the player are not
// loaded (no spawn chunks on newer versions), and an unverified fill fails silently, leaving the
// client inside natural terrain of a random world.
async function build(dimension, [x1, z1, x2, z2], fills) {
  const scoped = command => `execute in minecraft:${dimension} run ${command}`
  await consoleCommand(scoped(`forceload add ${x1} ${z1} ${x2} ${z2}`))
  for (const fill of fills) {
    const end = Date.now() + 20000
    for (;;) {
      const start = messages.length
      bot.chat('/' + scoped(fill))
      const done = () => messages.slice(start).some(m => m.includes('Successfully filled') || m.includes('No blocks were filled'))
      const answered = () => done() || messages.length > start
      for (let waited = 0; !answered() && waited < 2000; waited += 100) await delay(100)
      if (done()) break
      if (Date.now() > end) throw new Error(`Test terrain was not built: ${fill}; ${messages.slice(-5).join('; ')}`)
      await delay(500)
    }
  }
}

// The four right-click wands of the QA pack, by the display name their config gives them.
const wands = {
  slot: { id: 'qa_slot_cd', title: 'QA Slot cooldown', fired: 'QA slot fired' },
  groupA: { id: 'qa_group_a', title: 'QA Group A', fired: 'QA group A fired' },
  groupReset: { id: 'qa_group_reset', title: 'QA Group reset', fired: 'QA group reset fired' },
  audit: { id: 'qa_audit', title: 'QA Command audit', fired: 'QA audit continued after invalid command' }
}
const describe = item => JSON.stringify(item, (key, value) => typeof value === 'bigint' ? value.toString() : value)
const held = wand => bot.inventory.slots.filter(Boolean).find(item => describe(item).includes(wand.title))
async function use(wand) {
  const item = held(wand)
  assert(item, `${wand.id} is in the inventory with its configured name`)
  await bot.equip(item, 'hand')
  await delay(400)
  bot.activateItem()
  await delay(150)
  bot.deactivateItem()
}
async function fires(wand) {
  const start = messages.length
  await use(wand)
  await until(() => messages.slice(start).some(m => m.includes(wand.fired)), `${wand.id} ability fires`)
}
async function blocked(wand, why) {
  const start = messages.length
  await use(wand)
  await delay(1500)
  assert(!messages.slice(start).some(m => m.includes(wand.fired)), `${wand.id} must stay blocked: ${why}`)
}
async function score(expected) {
  await chat(`/scoreboard players get ${name} fiw_qa`, `${name} has ${expected} `)
}
function installPack() {
  const target = path.join(directory, 'config', 'fiw_tools')
  for (const folder of ['items', 'recipes']) {
    fs.mkdirSync(path.join(target, folder), { recursive: true })
    for (const file of fs.readdirSync(path.join(qa, folder))) {
      fs.copyFileSync(path.join(qa, folder, file), path.join(target, folder, file))
    }
  }
  fs.copyFileSync(path.join(qa, 'settings', 'file.json'), path.join(target, 'settings.json'))
}
function auditLines() {
  const file = path.join(directory, 'config', 'fiw_tools', 'logs', 'commands.jsonl')
  assert(fs.existsSync(file), `command audit log exists at ${file}`)
  return fs.readFileSync(file, 'utf8').split('\n').filter(Boolean).map(line => JSON.parse(line))
}

async function main() {
  await once(bot, 'spawn')
  bot.physicsEnabled = false
  await chunksLoaded()
  await consoleCommand(`op ${name}`)
  await consoleCommand('difficulty peaceful')
  await consoleCommand(`gamemode survival ${name}`)
  await build('overworld', [-2, -2, 4, 2], ['fill -2 64 -2 4 64 2 minecraft:stone', 'fill -2 65 -2 4 68 2 minecraft:air'])
  await consoleCommand(`tp ${name} 0.5 65 0.5`)
  await until(() => Math.abs(bot.entity.position.x - 0.5) < 0.15 && Math.abs(bot.entity.position.y - 65) < 0.15, 'arrival on the test platform')

  if (phase === 'first') {
    installPack()
    await consoleCommand('fiwtools reload')
    await chat('/fiwtools list', 'Loaded 7 item(s)')
    await chat('/fiwtools recipes', 'qa_slot_recipe')
    await consoleCommand('scoreboard objectives add fiw_qa dummy')
    await consoleCommand(`scoreboard players set ${name} fiw_qa 0`)
    await consoleCommand(`clear ${name}`)
    for (const wand of Object.values(wands)) await consoleCommand(`fiwtools give ${name} ${wand.id}`)
    await until(() => Object.values(wands).every(held), 'all four QA items arrive with their configured names')
    emit({ assertion: 'config hot-reload loads the QA pack and /fiwtools give delivers named custom items' })

    await fires(wands.slot)
    await blocked(wands.slot, 'its own 60 s cooldown')
    await consoleCommand(`fiwtools give ${name} ${wands.slot.id}`)
    await delay(500)
    await blocked(wands.slot, 'a second copy shares the player cooldown')
    await fires(wands.groupA)
    await blocked(wands.groupReset, 'it shares cooldown group QA_SHARED / qa_shared with group A')
    await fires(wands.audit)
    await score(3)
    await consoleCommand('fiwtools reload')
    await delay(500)
    await blocked(wands.slot, 'a config reload keeps the armed cooldown')
    await score(3)
    emit({ assertion: 'right-click abilities run their commands once; own, duplicate-copy and shared-group cooldowns block; reload keeps cooldowns' })
  } else if (phase === 'restart') {
    await until(() => Object.values(wands).every(held), 'custom items survive a server restart with their names')
    await score(3)
    await blocked(wands.slot, 'the cooldown was saved on stop and restored on join')
    await blocked(wands.groupReset, 'the shared group cooldown was saved on stop and restored on join')
    await fires(wands.audit)
    await score(4)
    const lines = auditLines()
    const audited = lines.filter(line => JSON.stringify(line).includes('qa_audit'))
    assert(audited.length >= 3, `audit log has the first session's commands (${audited.length} lines)`)
    assert(lines.some(line => JSON.stringify(line).includes('qa_intentionally_missing')), 'the failing command was audited too')
    assert(lines.every(line => JSON.stringify(line).includes(name)), 'every audit line names the player')
    emit({ assertion: 'items, cooldowns, shared groups, score and the flushed command audit log survive a restart' })
  } else throw new Error(`Unknown test phase: ${phase}`)

  const ended = once(bot, 'end')
  bot.quit('Verification complete')
  await ended
  clearTimeout(deadline)
  emit({ passed: phase, version })
}

main().catch(error => { emit({ error: error.stack }); bot.quit(); process.exit(1) })
