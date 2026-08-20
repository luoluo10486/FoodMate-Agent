import { execFileSync } from 'node:child_process';
import { renameSync } from 'node:fs';
import { resolve } from 'node:path';

const sourcePath = process.argv[2];
const destinationPath = process.argv[3] ?? sourcePath;

if (!sourcePath) {
  console.error('Usage: node scripts/convert-browser-capture-to-png.mjs <source> [destination]');
  process.exit(2);
}

const source = resolve(sourcePath).replaceAll("'", "''");
const destination = resolve(destinationPath).replaceAll("'", "''");
const temporaryDestination = `${destination}.tmp.png`;
const command = [
  'Add-Type -AssemblyName System.Drawing',
  `$image = [System.Drawing.Image]::FromFile('${source}')`,
  `try { $image.Save('${temporaryDestination}', [System.Drawing.Imaging.ImageFormat]::Png) } finally { $image.Dispose() }`,
].join('; ');

execFileSync('powershell.exe', ['-NoProfile', '-Command', command], { stdio: 'inherit' });
renameSync(temporaryDestination, destination);
