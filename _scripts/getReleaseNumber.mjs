import { fetch } from 'undici'
import packageJSON from '../package.json' with { type: 'json' }

const latestRelease = JSON.parse(await (await fetch('https://api.github.com/repos/MarmadileManteater/FreetubeAndroid/releases?per_page=1')).text())

const code = latestRelease[0].name

const [_, latestMajor, latestMinor, latestBuild] = code.split('.')

const [__, major, minor] = packageJSON.version.split('.')

if (
  latestMajor == major &&
  latestMinor == minor
) {
  console.log(parseInt(latestBuild) + 1)
} else {
  console.log(1)
}