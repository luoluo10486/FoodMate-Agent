import { readFile } from 'node:fs/promises';
import { inflateSync } from 'node:zlib';

function paeth(a, b, c) {
  const p = a + b - c;
  const pa = Math.abs(p - a);
  const pb = Math.abs(p - b);
  const pc = Math.abs(p - c);
  if (pa <= pb && pa <= pc) return a;
  if (pb <= pc) return b;
  return c;
}

function decodePng(buffer) {
  const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  if (!buffer.subarray(0, 8).equals(signature)) throw new Error('Not a PNG file');

  let offset = 8;
  let width;
  let height;
  let bitDepth;
  let colorType;
  const compressed = [];
  while (offset < buffer.length) {
    const length = buffer.readUInt32BE(offset);
    const type = buffer.toString('ascii', offset + 4, offset + 8);
    const data = buffer.subarray(offset + 8, offset + 8 + length);
    offset += 12 + length;
    if (type === 'IHDR') {
      width = data.readUInt32BE(0);
      height = data.readUInt32BE(4);
      bitDepth = data[8];
      colorType = data[9];
      if (bitDepth !== 8 || ![2, 6].includes(colorType)) {
        throw new Error(`Unsupported PNG format: bitDepth=${bitDepth}, colorType=${colorType}`);
      }
    } else if (type === 'IDAT') {
      compressed.push(data);
    } else if (type === 'IEND') {
      break;
    }
  }
  if (!width || !height) throw new Error('PNG is missing IHDR');

  const channels = colorType === 6 ? 4 : 3;
  const rowBytes = width * channels;
  const raw = inflateSync(Buffer.concat(compressed));
  const pixels = Buffer.alloc(width * height * 4);
  let inputOffset = 0;
  let previous = Buffer.alloc(rowBytes);
  for (let y = 0; y < height; y += 1) {
    const filter = raw[inputOffset++];
    const row = Buffer.alloc(rowBytes);
    for (let x = 0; x < rowBytes; x += 1) {
      const value = raw[inputOffset++];
      const left = x >= channels ? row[x - channels] : 0;
      const up = previous[x] ?? 0;
      const upperLeft = x >= channels ? previous[x - channels] : 0;
      if (filter === 0) row[x] = value;
      else if (filter === 1) row[x] = (value + left) & 0xff;
      else if (filter === 2) row[x] = (value + up) & 0xff;
      else if (filter === 3) row[x] = (value + Math.floor((left + up) / 2)) & 0xff;
      else if (filter === 4) row[x] = (value + paeth(left, up, upperLeft)) & 0xff;
      else throw new Error(`Unsupported PNG filter: ${filter}`);
    }
    for (let x = 0; x < width; x += 1) {
      const source = x * channels;
      const target = (y * width + x) * 4;
      pixels[target] = row[source];
      pixels[target + 1] = row[source + 1];
      pixels[target + 2] = row[source + 2];
      pixels[target + 3] = channels === 4 ? row[source + 3] : 255;
    }
    previous = row;
  }
  return { width, height, pixels };
}

const [expectedPath, actualPath] = process.argv.slice(2);
if (!expectedPath || !actualPath) {
  console.error('Usage: node scripts/png-diff.mjs <expected.png> <actual.png>');
  process.exit(2);
}

const [expected, actual] = await Promise.all([readFile(expectedPath), readFile(actualPath)]);
const left = decodePng(expected);
const right = decodePng(actual);
if (left.width !== right.width || left.height !== right.height) {
  console.log(JSON.stringify({
    status: 'SIZE_MISMATCH',
    expected: { width: left.width, height: left.height },
    actual: { width: right.width, height: right.height },
  }));
  process.exit(0);
}

let differentPixels = 0;
let absoluteError = 0;
let squaredError = 0;
let maxChannelDelta = 0;
for (let index = 0; index < left.pixels.length; index += 4) {
  let pixelDelta = 0;
  for (let channel = 0; channel < 4; channel += 1) {
    const delta = Math.abs(left.pixels[index + channel] - right.pixels[index + channel]);
    pixelDelta += delta;
    absoluteError += delta;
    squaredError += delta * delta;
    maxChannelDelta = Math.max(maxChannelDelta, delta);
  }
  if (pixelDelta > 0) differentPixels += 1;
}
const pixelCount = left.width * left.height;
const channelCount = pixelCount * 4;
console.log(JSON.stringify({
  status: 'COMPARED',
  width: left.width,
  height: left.height,
  pixelCount,
  differentPixels,
  differentRatio: differentPixels / pixelCount,
  meanAbsoluteError: absoluteError / channelCount,
  rmse: Math.sqrt(squaredError / channelCount),
  maxChannelDelta,
}));
