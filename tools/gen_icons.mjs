// 用 sharp 从 f.png 生成 Android 图标（sharp 来自 Firefly 项目的 node_modules）
// 运行：cd /home/qwc/gree && node tools/gen_icons.mjs
import { createRequire } from 'module'
import { fileURLToPath } from 'url'
import path from 'path'
import fs from 'fs'

// sharp 装在 /home/qwc/Firefly 的 node_modules 里，用 createRequire 从那边解析
const require = createRequire('/home/qwc/Firefly/')
const sharp = require('sharp')

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(__dirname, '..')
const SRC = path.join(ROOT, 'app/src/main/res')
const PNG = path.join(ROOT, 'f.png')

const DENSITIES = {
  mdpi: 48,
  hdpi: 72,
  xhdpi: 96,
  xxhdpi: 144,
  xxxhdpi: 192,
}

function dir(d) {
  const p = path.join(SRC, d)
  fs.mkdirSync(p, { recursive: true })
  return p
}

async function main() {
  const img = sharp(PNG)
  const meta = await img.metadata()
  console.log('source', meta.width, 'x', meta.height, 'channels', meta.channels)

  // 1) 常规 launcher 图标（各密度方形）
  for (const [density, size] of Object.entries(DENSITIES)) {
    await sharp(PNG)
      .resize(size, size, { fit: 'cover' })
      .png()
      .toFile(path.join(dir(`mipmap-${density}`), 'ic_launcher.png'))
    console.log('ic_launcher.png', density, size)
  }

  // 2) round 图标（圆形遮罩，用于 roundIcon）
  for (const [density, size] of Object.entries(DENSITIES)) {
    const s = sharp(PNG)
      .resize(size, size, { fit: 'cover' })
    // 圆形遮罩
    const mask = Buffer.from(
      `<svg width="${size}" height="${size}"><circle cx="${size / 2}" cy="${size / 2}" r="${size / 2}" fill="white"/></svg>`
    )
    await s
      .composite([{ input: mask, blend: 'dest-in' }])
      .png()
      .toFile(path.join(dir(`mipmap-${density}`), 'ic_launcher_round.png'))
    console.log('ic_launcher_round.png', density, size)
  }

  // 3) adaptive 前景：108dp 视口内，内容缩到 66/108=61% 居中（透明画布 432x432）
  const fgSize = 432
  const inner = Math.round(fgSize * 0.61) // ~264
  const offset = Math.round((fgSize - inner) / 2)
  const fgCanvas = Buffer.from(
    `<svg width="${fgSize}" height="${fgSize}"/>`
  )
  const resized = await sharp(PNG)
    .resize(inner, inner, { fit: 'cover' })
    .toBuffer()
  await sharp(fgCanvas)
    .composite([{ input: resized, left: offset, top: offset }])
    .png()
    .toFile(path.join(dir('drawable-nodpi'), 'ic_launcher_foreground.png'))
  console.log('ic_launcher_foreground.png', fgSize, 'inner', inner)

  console.log('done')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})