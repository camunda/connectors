// Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more
// contributor license agreements. Licensed under the Apache License, Version 2.0.
//
// Generic executor: runs one managed script per call, in a fresh child process (ADR-0008).
//
// Shared by every provider's adapter. The script arrives as data in the request: nothing is
// deployed per script. The wrapper is trusted; the child runs user code.
//
// On a cloud executor, after every call the wrapper kills every process that was not there when
// the executor was created and removes every new entry in the temporary directory, so nothing
// survives into the next call. In local mode, on a host shared with other processes, it kills only
// the process group of the script and removes only the directory of the call.

import { spawn } from 'node:child_process';
import {
  chmodSync, closeSync, fstatSync, lstatSync, mkdtempSync, openSync, readdirSync, readFileSync,
  readSync, rmdirSync, unlinkSync, writeFileSync, writeSync,
} from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const CONTRACT_VERSION = '1';
const CHILD = fileURLToPath(new URL('./child.mjs', import.meta.url));
const MAX_OUTPUT_BYTES = 1_000_000;
const MAX_LOG_BYTES = 4_096;
// V8 prints its fatal error before a native backtrace of several KB: look past the backtrace.
const MAX_DIAGNOSTIC_BYTES = 65_536;
const MAX_MESSAGE_LENGTH = 1024;
const MAX_STACK_TRACE_LENGTH = 8192;
const CHILD_HEAP_MB = 256;
const DEFAULT_TIMEOUT_MS = 10_000;
const SWEEP_ROUNDS = 5;
// The only failures the child may report: the others are the platform's.
const CHILD_FAILURE_CODES = ['SCRIPT_ERROR', 'INVALID_RESULT', 'RESOURCE_LIMIT'];
const SCRUBBED_VARIABLES = ['AWS_ACCESS_KEY_ID=', 'AWS_SECRET_ACCESS_KEY=', 'AWS_SESSION_TOKEN='];

function pids() {
  try {
    return new Set(readdirSync('/proc').filter((entry) => /^\d+$/.test(entry)).map(Number));
  } catch {
    return new Set(); // macOS: no /proc, only the process group is killed
  }
}

function ancestors() {
  const seen = new Set();
  let pid = process.pid;
  while (pid > 0 && !seen.has(pid)) {
    seen.add(pid);
    try {
      // The command name is in parentheses and may contain spaces: read after the last one.
      const stat = readFileSync(`/proc/${pid}/stat`, 'utf8');
      pid = Number(stat.slice(stat.lastIndexOf(')') + 2).split(' ')[1]);
    } catch {
      break;
    }
  }
  return seen;
}

function zombie(pid) {
  try {
    const stat = readFileSync(`/proc/${pid}/stat`, 'utf8');
    return stat.slice(stat.lastIndexOf(')') + 2).split(' ')[0] === 'Z';
  } catch {
    return true; // gone
  }
}

// Every path under `directory`, relative to it. Symlinks are listed, never followed.
function snapshot(directory, prefix = '') {
  const found = new Set();
  let children = [];
  try {
    children = readdirSync(path.join(directory, prefix), { withFileTypes: true });
  } catch {
    return found;
  }
  for (const child of children) {
    const relative = path.join(prefix, child.name);
    found.add(relative);
    if (child.isDirectory()) for (const nested of snapshot(directory, relative)) found.add(nested);
  }
  return found;
}

function allowOwner(target) {
  try {
    chmodSync(target, lstatSync(target).mode | 0o700);
  } catch {
    // Not ours to change.
  }
}

// Removes a file, a link or a whole directory tree, and never follows a link. A script can take the
// permissions off a directory, so that its content cannot be listed or removed: the owner gets them
// back on every directory of the tree before it is read. Never throws.
function remove(target) {
  const directories = [];
  const pending = [target];
  while (pending.length > 0) {
    const current = pending.pop();
    let isDirectory;
    try {
      isDirectory = lstatSync(current).isDirectory();
    } catch {
      continue;
    }
    if (!isDirectory) {
      try { unlinkSync(current); } catch { /* not ours to remove */ }
      continue;
    }
    allowOwner(current);
    try {
      for (const name of readdirSync(current)) pending.push(path.join(current, name));
    } catch { /* not readable: removed empty or left */ }
    directories.push(current);
  }
  for (const directory of directories.reverse()) { // the deepest first
    try { rmdirSync(directory); } catch { /* not empty: not ours */ }
  }
}

// Removes everything under `directory` that is not in `baseline`, also inside the directories that
// were already there: a writable directory of the baseline must not carry files to the next call.
function removeNew(directory, baseline, prefix = '') {
  // Entries are unlinked from their parent: a directory of the baseline that the script made
  // read-only gets its owner permissions back (the directory itself is left alone).
  if (prefix) allowOwner(path.join(directory, prefix));
  let children = [];
  try {
    children = readdirSync(path.join(directory, prefix), { withFileTypes: true });
  } catch {
    return;
  }
  for (const child of children) {
    const relative = path.join(prefix, child.name);
    if (!baseline.has(relative)) {
      remove(path.join(directory, relative));
    } else if (child.isDirectory()) {
      removeNew(directory, baseline, relative);
    }
  }
}

// Overwrites the credentials in this process's initial environment block, which is what
// /proc/<pid>/environ shows to other processes of the same user. The wrapper never calls a cloud
// API, so it does not need them. Node.js has no prctl(PR_SET_DUMPABLE) to hide the whole file.
function scrubEnvironmentBlock() {
  for (const name of SCRUBBED_VARIABLES) delete process.env[name.slice(0, -1)];
  if (process.platform !== 'linux') return;
  try {
    const stat = readFileSync('/proc/self/stat', 'utf8');
    const fields = stat.slice(stat.lastIndexOf(')') + 2).split(' ');
    const start = Number(fields[47]); // fields 50 and 51: env_start, env_end
    const end = Number(fields[48]);
    const fd = openSync('/proc/self/mem', 'r+');
    try {
      const block = Buffer.alloc(end - start);
      readSync(fd, block, 0, block.length, start);
      let offset = 0;
      for (const entry of block.toString('latin1').split('\0')) {
        if (SCRUBBED_VARIABLES.some((name) => entry.startsWith(name))) {
          block.fill(0, offset, offset + entry.length);
        }
        offset += entry.length + 1;
      }
      writeSync(fd, block, 0, block.length, start);
    } finally {
      closeSync(fd);
    }
  } catch {
    // Best effort: the child never inherits this environment anyway.
  }
}

// Reads through a descriptor the wrapper opened before the script started: the script cannot
// redirect it by replacing or removing the file it points to.
function read(fd, limit, position = 0) {
  const buffer = Buffer.alloc(limit);
  return buffer.subarray(0, readSync(fd, buffer, 0, limit, position));
}

function readTail(fd, limit) {
  return read(fd, limit, Math.max(0, fstatSync(fd).size - limit));
}

function failure(code, message) {
  return {
    contractVersion: CONTRACT_VERSION,
    outcome: 'FAILED',
    error: { code, message: message.slice(0, MAX_MESSAGE_LENGTH), retryable: false, truncated: false },
  };
}

const isObject = (value) => value !== null && typeof value === 'object' && !Array.isArray(value);

// JSON.parse turns 1e999 into Infinity, which JSON.stringify writes as null: a number that is not
// finite in a response the script wrote itself is not a contract response.
function finite(key, value) {
  if (typeof value === 'number' && !Number.isFinite(value)) throw new RangeError(`${key} is out of range`);
  return value;
}

// The child is not trusted: keep only contract fields, so a script cannot forge a platform failure
// or ask to be retried.
function sanitize(response) {
  if (isObject(response) && response.outcome === 'COMPLETED' && isObject(response.variables)) {
    return { contractVersion: CONTRACT_VERSION, outcome: 'COMPLETED', variables: response.variables };
  }
  if (isObject(response) && response.outcome === 'FAILED' && isObject(response.error)
      && CHILD_FAILURE_CODES.includes(response.error.code)) {
    const { code, message, stackTrace, truncated } = response.error;
    const error = { code, message: String(message ?? '').slice(0, MAX_MESSAGE_LENGTH), retryable: false };
    error.truncated = truncated === true;
    if (typeof stackTrace === 'string') {
      error.stackTrace = stackTrace.slice(0, MAX_STACK_TRACE_LENGTH);
      error.truncated = error.truncated || stackTrace.length > MAX_STACK_TRACE_LENGTH;
    }
    return { contractVersion: CONTRACT_VERSION, outcome: 'FAILED', error };
  }
  return failure('INVALID_RESULT', 'the child did not return a contract response');
}

function childEnv(workdir) {
  // No platform variables: credentials and the platform's addresses stay in the wrapper.
  return {
    PATH: process.env.EXECUTOR_CHILD_PATH || '/usr/local/bin:/usr/bin:/bin',
    HOME: workdir,
    TMPDIR: workdir,
    LANG: 'C.UTF-8',
  };
}

function validate(event) {
  if (event.contractVersion !== CONTRACT_VERSION) {
    return failure('UNSUPPORTED_CONTRACT_VERSION',
      `contract version ${JSON.stringify(event.contractVersion)} is not supported, expected ${CONTRACT_VERSION}`);
  }
  if (typeof event.script !== 'string' || event.script.length === 0) {
    return failure('INVALID_INPUT', "'script' must be a non-empty string");
  }
  if (!isObject(event.variables)) return failure('INVALID_INPUT', "'variables' must be a JSON object");
  if (event.context !== undefined && !isObject(event.context)) {
    return failure('INVALID_INPUT', "'context' must be a JSON object");
  }
  return null;
}

async function execute(event, workdir, deadlineMs) {
  writeFileSync(path.join(workdir, 'user_script.mjs'), event.script);
  const request = { variables: event.variables, context: event.context ?? {} };
  const timeoutMs = Math.max(1, Math.min(Number(event.timeoutMs) || DEFAULT_TIMEOUT_MS, deadlineMs));

  // Output goes to files, not pipes: a leftover process holding a pipe open would block the read.
  const stdoutPath = path.join(workdir, '.stdout');
  const stderrPath = path.join(workdir, '.stderr');
  // Every descriptor opened here is closed on every path, also when a later open or the spawn fails.
  const opened = new Set();
  const open = (file, flags) => {
    const fd = openSync(file, flags);
    opened.add(fd);
    return fd;
  };
  const release = (fd) => {
    if (!opened.delete(fd)) return;
    try { closeSync(fd); } catch { /* already closed */ }
  };
  try {
    const stdoutFd = open(stdoutPath, 'w');
    const stderrFd = open(stderrPath, 'w');
    const stdoutReadFd = open(stdoutPath, 'r');
    const stderrReadFd = open(stderrPath, 'r');
    let timedOut = false;
    let childPid = null;
    const exitCode = await new Promise((resolve) => {
      const child = spawn(process.execPath, [`--max-old-space-size=${CHILD_HEAP_MB}`, CHILD, workdir], {
        cwd: workdir,
        env: childEnv(workdir),
        stdio: ['pipe', stdoutFd, stderrFd],
        detached: true, // own process group, killed as a whole on timeout
      });
      childPid = child.pid;
      const timer = setTimeout(() => {
        timedOut = true;
        try { process.kill(-child.pid, 'SIGKILL'); } catch { /* already gone */ }
      }, timeoutMs);
      child.on('exit', (code, signal) => {
        clearTimeout(timer);
        // Before anything is read, so that a failure to read cannot leave the script's processes behind.
        try { process.kill(-child.pid, 'SIGKILL'); } catch { /* already gone */ }
        resolve(code ?? signal);
      });
      child.on('error', () => {
        clearTimeout(timer);
        resolve(null);
      });
      child.stdin.on('error', () => {});
      child.stdin.end(JSON.stringify(request));
    });
    release(stdoutFd);
    release(stderrFd);
    return finish(stdoutReadFd, stderrReadFd, { timedOut, timeoutMs, exitCode, childPid });
  } finally {
    for (const fd of [...opened]) release(fd);
  }
}

function finish(stdoutReadFd, stderrReadFd, { timedOut, timeoutMs, exitCode, childPid }) {
  const logs = read(stderrReadFd, MAX_LOG_BYTES);
  if (timedOut) {
    return { response: failure('TIMEOUT', `script exceeded ${timeoutMs} ms`), logs, childPid };
  }
  const out = read(stdoutReadFd, MAX_OUTPUT_BYTES + 1);
  if (out.length > MAX_OUTPUT_BYTES) {
    return { response: failure('RESOURCE_LIMIT', `output larger than ${MAX_OUTPUT_BYTES} bytes`), logs, childPid };
  }
  if (out.length === 0) {
    const response = readTail(stderrReadFd, MAX_DIAGNOSTIC_BYTES).includes('heap out of memory')
      ? failure('RESOURCE_LIMIT', 'script ran out of memory')
      : failure('PLATFORM_EXECUTION_FAILED', `child exited with ${exitCode}`);
    return { response, logs, childPid };
  }
  try {
    return { response: sanitize(JSON.parse(out.toString('utf8'), finite)), logs, childPid };
  } catch {
    return { response: failure('INVALID_RESULT', 'the script wrote to standard output outside the contract'), logs, childPid };
  }
}

/**
 * Creates the executor of one instance. Call it once, when the instance starts: on a cloud
 * executor, what exists at that moment is kept after every call, and anything else is removed.
 *
 * @param {{ local: boolean, tmp: string }} options `local` for a host shared with other processes;
 *     `tmp`, the directory where every call gets a directory of its own
 */
export function createExecutor({ local, tmp }) {
  const baselinePids = local ? null : pids();
  const baselineTmp = local ? null : snapshot(tmp);
  if (!local) scrubEnvironmentBlock();

  // A script can leave its process group with setsid or a double fork, so killing the group is not
  // enough. Repeats while new processes appear, to catch processes forked during the sweep.
  function sweep() {
    // Node.js cannot reap processes it did not spawn, so zombies are skipped, not counted.
    const killed = new Set();
    const protectedPids = new Set([...baselinePids, ...ancestors()]);
    for (let round = 0; round < SWEEP_ROUNDS; round += 1) {
      const targets = [...pids()].filter((pid) => !protectedPids.has(pid) && !killed.has(pid) && !zombie(pid));
      if (targets.length === 0) break;
      for (const pid of targets) {
        try {
          process.kill(pid, 'SIGKILL');
          killed.add(pid);
        } catch {
          protectedPids.add(pid);
        }
      }
    }
  }

  function cleanTmp() {
    removeNew(tmp, baselineTmp);
  }

  /** Runs the script of `event` and returns the contract response; never throws. */
  async function run(event, deadlineMs) {
    const invalid = validate(event ?? {});
    if (invalid) return invalid;
    let workdir = null;
    let result = { response: failure('PLATFORM_EXECUTION_FAILED', 'wrapper error'), logs: Buffer.alloc(0), childPid: null };
    try {
      workdir = mkdtempSync(path.join(tmp, 'run-'));
      result = await execute(event, workdir, deadlineMs);
    } catch (error) {
      // The executor must keep serving calls.
      result = { ...result, response: failure('PLATFORM_EXECUTION_FAILED', `wrapper error: ${error}`) };
    } finally {
      if (local) {
        if (result.childPid) {
          try { process.kill(-result.childPid, 'SIGKILL'); } catch { /* already gone */ }
        }
        if (workdir) remove(workdir);
      } else {
        sweep();
        cleanTmp();
      }
    }
    const logs = result.logs.toString('utf8');
    if (logs) process.stderr.write(`[script] ${logs}\n`);
    return result.response;
  }

  return { run };
}
