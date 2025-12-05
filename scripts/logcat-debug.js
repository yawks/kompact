#!/usr/bin/env node
const { spawn } = require('child_process');
const { existsSync } = require('fs');
const { join } = require('path');

const workspace = process.cwd();
const serialArg = (process.argv[2] || '').trim();
const envSerial = (process.env.KOMPACT_DEVICE_SERIAL || '').trim();
const deviceSerial = serialArg || envSerial;

const adbCandidate = join(workspace, '.android-sdk', 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
const adbExecutable = existsSync(adbCandidate) ? adbCandidate : 'adb';

const args = [];
if (deviceSerial) {
  args.push('-s', deviceSerial);
}
args.push('logcat', 'Kompact:D', 'ActivityManager:I', 'AndroidRuntime:E', '*:S');

console.log(`▶ Avvio logcat (${adbExecutable})${deviceSerial ? ` sul dispositivo ${deviceSerial}` : ''}`);
const child = spawn(adbExecutable, args, { stdio: 'inherit' });

child.on('exit', code => {
  console.log('⏹️  Logcat terminato');
  process.exit(code ?? 0);
});

child.on('error', err => {
  console.error('Errore avviando adb:', err.message);
  process.exit(1);
});
