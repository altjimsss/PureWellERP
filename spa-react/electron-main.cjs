const { app, BrowserWindow, Menu } = require('electron');
const path = require('path');
const { spawn } = require('child_process');
const net = require('net');
const fs = require('fs');

let mainWindow;
let javaProcess;

const loadDotEnv = (filePath) => {
  const result = {};
  if (!fs.existsSync(filePath)) {
    return result;
  }

  const content = fs.readFileSync(filePath, 'utf8');
  for (const rawLine of content.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) {
      continue;
    }

    const eqIndex = line.indexOf('=');
    if (eqIndex <= 0) {
      continue;
    }

    const key = line.slice(0, eqIndex).trim();
    let value = line.slice(eqIndex + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    result[key] = value;
  }

  return result;
};

// Function to check if port 8080 is listening
const waitForServer = (port = 8080, maxRetries = 30, retryDelay = 1000) => {
  return new Promise((resolve) => {
    let retries = 0;

    const tryConnect = () => {
      const socket = new net.Socket();
      socket.setTimeout(1000);

      socket.on('connect', () => {
        socket.destroy();
        console.log(`✓ Backend server is ready on port ${port}`);
        resolve(true);
      });

      socket.on('timeout', () => {
        socket.destroy();
        attemptRetry();
      });

      socket.on('error', () => {
        socket.destroy();
        attemptRetry();
      });

      socket.connect(port, 'localhost');
    };

    const attemptRetry = () => {
      retries++;
      if (retries < maxRetries) {
        console.log(`Waiting for backend... (${retries}/${maxRetries})`);
        setTimeout(tryConnect, retryDelay);
      } else {
        console.warn('Backend server not responding, continuing anyway...');
        resolve(false);
      }
    };

    tryConnect();
  });
};

// Start Spring Boot backend
const startBackend = () => {
  return new Promise((resolve) => {
    // Find backend.jar by looking at the executable location
    // process.execPath is the actual PureWell ERP.exe being run
    const exeDir = path.dirname(process.execPath);
    const jarPath = path.join(exeDir, 'backend.jar');
    const envPath = path.join(exeDir, '.env');
    
    if (!fs.existsSync(jarPath)) {
      console.error('❌ JAR not found at:', jarPath);
      resolve(false);
      return;
    }
    
    const envFromFile = loadDotEnv(envPath);
    const backendLogPath = path.join(exeDir, 'backend.log');
    const backendLogFd = fs.openSync(backendLogPath, 'a');

    // Start backend without showing any window
    javaProcess = spawn('java', ['-Xmx2048M', '-jar', jarPath], {
      stdio: ['ignore', backendLogFd, backendLogFd],
      detached: false,           // Will die when parent dies
      windowsHide: true,         // Hide window on Windows
      cwd: exeDir,
      env: {
        ...process.env,
        ...envFromFile,
      },
    });

    let backendReady = false;
    
    javaProcess.on('error', (err) => {
      console.error('Failed to start backend:', err.message);
      resolve(false);
    });

    // Wait up to 15 seconds for backend to be ready
    let attempts = 0;
    const checkServer = () => {
      if (backendReady) return;
      
      attempts++;
      if (attempts > 15) {
        console.warn('Backend startup timeout - continuing anyway');
        resolve(true);
        return;
      }
      
      const net = require('net');
      const socket = new net.Socket();
      
      socket.setTimeout(1000);
      socket.on('connect', () => {
        backendReady = true;
        socket.destroy();
        resolve(true);
      });
      socket.on('timeout', () => {
        socket.destroy();
        setTimeout(checkServer, 1000);
      });
      socket.on('error', () => {
        setTimeout(checkServer, 1000);
      });
      
      socket.connect(8080, '127.0.0.1');
    };
    
    setTimeout(checkServer, 500);
  });
};

const createWindow = () => {
  const runtimeIcon = app.isPackaged
    ? path.join(__dirname, 'electron-dist', 'purewelllogo.png')
    : path.join(__dirname, 'public', 'purewelllogo.png');

  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    autoHideMenuBar: true,
    webPreferences: {
      nodeIntegration: false,
      enableRemoteModule: false,
      preload: path.join(__dirname, 'electron-preload.cjs'),
    },
    icon: runtimeIcon,
  });

  if (!app.isPackaged) {
    mainWindow.loadURL('http://localhost:5173');
  } else {
    mainWindow.loadURL('http://127.0.0.1:8080/spa/');
  }

  if (!app.isPackaged) {
    mainWindow.webContents.openDevTools();
  }

  mainWindow.setMenuBarVisibility(false);

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
};

app.on('ready', async () => {
  try {
    console.log('🚀 PureWell ERP Starting...');
    Menu.setApplicationMenu(null);
    
    if (app.isPackaged) {
      console.log('Starting backend service...');
      await startBackend();
      console.log('Backend is ready!');
    }
    
    console.log('Opening frontend window...');
    createWindow();
  } catch (err) {
    console.error('Failed to start app:', err);
    app.quit();
  }
});

app.on('window-all-closed', () => {
  // Kill the Java process when all windows close
  if (javaProcess && !javaProcess.killed) {
    console.log('Stopping backend service...');
    javaProcess.kill('SIGTERM');
  }
  
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('activate', () => {
  if (mainWindow === null) {
    createWindow();
  }
});

app.on('before-quit', () => {
  if (javaProcess && !javaProcess.killed) {
    javaProcess.kill('SIGTERM');
  }
});
