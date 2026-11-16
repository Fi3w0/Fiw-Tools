// Console-only checks for Minecraft versions the headless client cannot join yet (see
// NO_CLIENT_MINECRAFT in targets.sh). It loads the QA pack (examples/qa) into a production server
// and checks that every item and recipe builds without a failure. Nothing here uses a player, so
// abilities, cooldowns and decoys are not covered: those need the client test (tools.cjs).
// stdout is a JSON bridge to the runner: {"console": ...} lines become server console input, and
// console responses are read back from the server log of this session.
const fs = require('node:fs')
const path = require('node:path')

const [, version, phase, directory] = process.argv.slice(2)
const qa = path.join(__dirname, '..', '..', 'examples', 'qa')
const log = path.join(directory, `${phase}.log`)
const emit = value => process.stdout.write(JSON.stringify(value) + '\n')
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))
const deadline = setTimeout(() => { emit({ error: 'Console verification timed out' }); process.exit(1) }, 120000)

const text = () => fs.readFileSync(log, 'utf8')
// Sends a command and waits until its response appears in the log.
async function expect(command, response, timeout = 15000) {
  const start = text().length
  emit({ console: command })
  const end = Date.now() + timeout
  for (;;) {
    const output = text().slice(start)
    if (output.includes(response)) return output
    if (Date.now() > end) {
      throw new Error(`Timed out: ${command} -> ${response}; last output:\n${output.split('\n').slice(-8).join('\n')}`)
    }
    await delay(200)
  }
}
function installPack() {
  const target = path.join(directory, 'config', 'fiw_tools')
  const counts = {}
  for (const folder of ['items', 'recipes']) {
    fs.mkdirSync(path.join(target, folder), { recursive: true })
    const files = fs.readdirSync(path.join(qa, folder)).filter(file => file.endsWith('.json'))
    for (const file of files) fs.copyFileSync(path.join(qa, folder, file), path.join(target, folder, file))
    counts[folder] = files.length
  }
  fs.copyFileSync(path.join(qa, 'settings', 'file.json'), path.join(target, 'settings.json'))
  return counts
}

async function main() {
  // The pack is written in the first session and must still load from disk after the restart.
  const counts = phase === 'first'
    ? installPack()
    : Object.fromEntries(['items', 'recipes'].map(folder =>
      [folder, fs.readdirSync(path.join(qa, folder)).filter(file => file.endsWith('.json')).length]))
  await expect('fiwtools reload', `Reloaded: ${counts.items} item(s), ${counts.recipes} recipe(s), 0 failed`)
  await expect('fiwtools list', `Loaded ${counts.items} item(s)`)
  await expect('fiwtools recipes', 'qa_slot_recipe')
  emit({ assertion: `the QA pack builds ${counts.items} items and ${counts.recipes} recipe(s) with no failure (${phase})` })
  clearTimeout(deadline)
  emit({ passed: phase, version, console_only: true })
  setTimeout(() => process.exit(0), 300)
}

main().catch(error => { emit({ error: error.stack }); process.exit(1) })
