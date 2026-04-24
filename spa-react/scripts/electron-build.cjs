const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const spaRoot = path.resolve(__dirname, '..');
const projectRoot = path.resolve(spaRoot, '..');
const distRoot = path.join(spaRoot, 'dist');
const unpackedDir = path.join(distRoot, 'win-unpacked');
const envPath = path.join(projectRoot, '.env');
const portableJreDir = path.join(projectRoot, 'portable-jre');
const deliverableDir = path.join(projectRoot, 'PureWell-ERP-Package_Final');
const deliverableZip = path.join(projectRoot, 'PureWell-ERP-Package_Final.zip');

function run(command, cwd) {
  console.log(`\n> ${command}`);
  execSync(command, {
    cwd,
    stdio: 'inherit',
    shell: true
  });
}

const mvnw = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw';

function ensureEnvExists() {
  if (!fs.existsSync(envPath)) {
    console.error('\nMissing required DB config: .env');
    console.error(`Expected at: ${envPath}`);
    console.error('Create it from .env.example, then run build again.');
    process.exit(1);
  }
}

function resolveJavaHome() {
  try {
    const output = execSync('java -XshowSettings:properties -version 2>&1', {
      cwd: projectRoot,
      encoding: 'utf8',
      shell: true
    });
    const match = output.match(/^\s*java\.home\s*=\s*(.+)\s*$/m);
    if (!match) {
      return null;
    }
    return match[1].trim();
  } catch (error) {
    return null;
  }
}

function preparePortableJre() {
  const javaBinName = process.platform === 'win32' ? 'java.exe' : 'java';
  const existingPortableJava = path.join(portableJreDir, 'bin', javaBinName);
  if (fs.existsSync(existingPortableJava)) {
    console.log(`\nReusing existing portable Java runtime: ${portableJreDir}`);
    return;
  }

  const javaHome = resolveJavaHome();
  if (!javaHome) {
    console.error('\nJava was not found on the build machine.');
    console.error(`Install Java 17+ first, or provide an existing portable runtime at: ${portableJreDir}`);
    process.exit(1);
  }

  const javaExe = path.join(javaHome, 'bin', javaBinName);
  if (!fs.existsSync(javaExe)) {
    console.error('\nCould not find java executable in detected java.home.');
    console.error(`java.home: ${javaHome}`);
    process.exit(1);
  }

  console.log(`\nPreparing portable Java runtime from: ${javaHome}`);
  fs.rmSync(portableJreDir, { recursive: true, force: true });
  fs.mkdirSync(path.dirname(portableJreDir), { recursive: true });
  fs.cpSync(javaHome, portableJreDir, { recursive: true });
}

function copyDeliverable() {
  if (!fs.existsSync(unpackedDir)) {
    console.error(`\nExpected Electron output not found: ${unpackedDir}`);
    process.exit(1);
  }

  fs.rmSync(deliverableDir, { recursive: true, force: true });
  fs.mkdirSync(deliverableDir, { recursive: true });
  fs.cpSync(unpackedDir, deliverableDir, { recursive: true });

  // Keep both names so runtime can find config even if dotfiles are hidden by tools.
  fs.copyFileSync(envPath, path.join(deliverableDir, '.env'));
  fs.copyFileSync(envPath, path.join(deliverableDir, 'db.env'));
}

function zipDeliverable() {
  fs.rmSync(deliverableZip, { force: true });
  const escapedZip = deliverableZip.replace(/'/g, "''");
  const escapedDir = deliverableDir.replace(/'/g, "''");

  run(
    `powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Path '${escapedDir}\\*' -DestinationPath '${escapedZip}' -Force"`,
    projectRoot
  );
}

try {
  ensureEnvExists();
  preparePortableJre();
  run('npm run web-build', spaRoot);
  run('npm run sync-spa-to-backend', spaRoot);
  run(`${mvnw} -DskipTests clean package`, projectRoot);
  run('npx electron-builder --win --publish never -c.forceCodeSigning=false', spaRoot);
  copyDeliverable();
  zipDeliverable();
  console.log(`\nBuild complete.`);
  console.log(`Deliverable folder: ${deliverableDir}`);
  console.log(`Deliverable zip:    ${deliverableZip}`);
} catch (error) {
  console.error('\nElectron build failed.');
  process.exit(1);
}
