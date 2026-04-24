const fs = require('fs');
const path = require('path');

const spaRoot = path.resolve(__dirname, '..');
const projectRoot = path.resolve(spaRoot, '..');
const sourceDir = path.join(spaRoot, 'electron-dist');
const targetDir = path.join(projectRoot, 'src', 'main', 'resources', 'static', 'spa');

if (!fs.existsSync(sourceDir)) {
  console.error(`Source build folder not found: ${sourceDir}`);
  process.exit(1);
}

fs.rmSync(targetDir, { recursive: true, force: true });
fs.mkdirSync(targetDir, { recursive: true });
fs.cpSync(sourceDir, targetDir, { recursive: true });

console.log(`Synced SPA assets:\n  from: ${sourceDir}\n  to:   ${targetDir}`);
