// Generic executor: runs one managed script per call, in a fresh child process.
//
// Shared by the AWS Lambda, Cloud Run and local adapters. The script arrives as data in the
// request: nothing is deployed per script. The wrapper is trusted; the child runs user code.
//
// After every call the wrapper kills every process that was not there when the instance started
// and removes every new entry in the temporary directory, so nothing survives into the next call.
//
// With EXECUTOR_LOCAL=1, the local provider of a developer machine, the wrapper shares the host with
// other processes: it kills only the process group of the script, and EXECUTOR_TMP is a directory of
// its own for the call.
//
// Ported from POC 0.11 of camunda/code-execution-poc.

import { spawn } from 'node:child_process';
import {
  closeSync, mkdtempSync, openSync, readdirSync, readFileSync, readSync, rmSync,
  writeFileSync, writeSync,
} from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const CONTRACT_VERSION = '1';
const CHILD = fileURLToPath(new URL('./child.mjs', import.meta.url));
const TMP = process.env.EXECUTOR_TMP || '/tmp';
const LOCAL = process.env.EXECUTOR_LOCAL === '1';
const MAX_OUTPUT_BYTES = 1_000_000;
const MAX_LOG_BYTES = 4_096;
const CHILD_HEAP_MB = 256;
const DEFAULT_TIMEOUT_MS = 10_000;
const SWEEP_ROUNDS = 5;


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

function entries(directory) {
  try {
    return new Set(readdirSync(directory));
  } catch {
    return new Set();
  }
}

const SCRUBBED_VARIABLES = ['AWS_ACCESS_KEY_ID=', 'AWS_SECRET_ACCESS_KEY=', 'AWS_SESSION_TOKEN='];

// Overwrites the credentials in this process's initial environment block, which is what
// /proc/<pid>/environ shows to other processes of the same user. The wrapper never calls a cloud
// API, so it does not need them. Node.js has no prctl(PR_SET_DUMPABLE) to hide the whole file.
function scrubEnvironmentBlock() {
  if (process.platform !== 'linux') return { error: 'not Linux' };
  try {
    const stat = readFileSync('/proc/self/stat', 'utf8');
    const fields = stat.slice(stat.lastIndexOf(')') + 2).split(' ');
    const start = Number(fields[47]); // fields 50 and 51: env_start, env_end
    const end = Number(fields[48]);
    const fd = openSync('/proc/self/mem', 'r+');
    try {
      const block = Buffer.alloc(end - start);
      readSync(fd, block, 0, block.length, start);
      let scrubbed = 0;
      let offset = 0;
      for (const entry of block.toString('latin1').split('\0')) {
        if (SCRUBBED_VARIABLES.some((name) => entry.startsWith(name))) {
          block.fill(0, offset, offset + entry.length);
          scrubbed += 1;
        }
        offset += entry.length + 1;
      }
      writeSync(fd, block, 0, block.length, start);
      return { scrubbed };
    } finally {
      closeSync(fd);
    }
  } catch (error) {
    return { error: String(error) };
  }
}

const BASELINE_PIDS = pids();
const BASELINE_TMP = entries(TMP);
if (!LOCAL) scrubEnvironmentBlock();

// A script can leave its process group with setsid or a double fork, so killing the group is not
// enough. Repeats while new processes appear, to catch processes forked during the sweep.
function sweep() {
  // Node.js cannot reap processes it did not spawn, so zombies are skipped, not counted.
  const killed = new Set();
  const protectedPids = new Set([...BASELINE_PIDS, ...ancestors()]);
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
  return killed.size;
}

function cleanTmp() {
  let removed = 0;
  for (const entry of entries(TMP)) {
    if (BASELINE_TMP.has(entry)) continue;
    try {
      rmSync(path.join(TMP, entry), { recursive: true, force: true });
      removed += 1;
    } catch {
      // Not ours to remove.
    }
  }
  return removed;
}

function read(file, limit) {
  const fd = openSync(file, 'r');
  try {
    const buffer = Buffer.alloc(limit);
    return buffer.subarray(0, readSync(fd, buffer, 0, limit, 0));
  } finally {
    closeSync(fd);
  }
}

function failure(code, message, retryable = false) {
  return {
    contractVersion: CONTRACT_VERSION,
    outcome: 'FAILED',
    error: { code, message: message.slice(0, 1024), retryable, truncated: false },
  };
}

// Keeps only contract fields, so a script cannot forge platform metadata.
function sanitize(response) {
  if (response === null || typeof response !== 'object'
      || !['COMPLETED', 'FAILED'].includes(response.outcome)) {
    return failure('INVALID_RESULT', 'the child did not return a contract response');
  }
  const allowed = ['contractVersion', 'outcome', 'variables', 'error'];
  return Object.fromEntries(allowed.filter((key) => key in response).map((key) => [key, response[key]]));
}

function childEnv(workdir) {
  // No platform variables: AWS_* credentials and the Runtime API address stay in the wrapper.
  return {
    PATH: process.env.EXECUTOR_CHILD_PATH || '/usr/local/bin:/usr/bin:/bin',
    HOME: workdir,
    TMPDIR: workdir,
    LANG: 'C.UTF-8',
  };
}

async function execute(event, workdir, deadlineMs) {
  if (typeof event.script !== 'string' || event.script.length === 0) {
    return { response: failure('INVALID_INPUT', "'script' must be a non-empty string"), logs: Buffer.alloc(0), childPid: null };
  }
  writeFileSync(path.join(workdir, 'user_script.mjs'), event.script);
  const request = {
    contractVersion: event.contractVersion,
    variables: event.variables,
    context: event.context ?? {},
  };
  const timeoutMs = Math.max(1, Math.min(Number(event.timeoutMs) || DEFAULT_TIMEOUT_MS, deadlineMs));

  // Output goes to files, not pipes: a leftover process holding a pipe open would block the read.
  const stdoutPath = path.join(workdir, '.stdout');
  const stderrPath = path.join(workdir, '.stderr');
  const stdoutFd = openSync(stdoutPath, 'w');
  const stderrFd = openSync(stderrPath, 'w');
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
      resolve(code ?? signal);
    });
    child.on('error', () => {
      clearTimeout(timer);
      resolve(null);
    });
    child.stdin.on('error', () => {});
    child.stdin.end(JSON.stringify(request));
  });
  closeSync(stdoutFd);
  closeSync(stderrFd);

  const logs = read(stderrPath, MAX_LOG_BYTES);
  if (timedOut) {
    return { response: failure('TIMEOUT', `script exceeded ${timeoutMs} ms`), logs, childPid };
  }
  const out = read(stdoutPath, MAX_OUTPUT_BYTES + 1);
  if (out.length > MAX_OUTPUT_BYTES) {
    return { response: failure('RESOURCE_LIMIT', `output larger than ${MAX_OUTPUT_BYTES} bytes`), logs, childPid };
  }
  if (out.length === 0) {
    const response = logs.includes('heap out of memory')
      ? failure('RESOURCE_LIMIT', 'script ran out of memory')
      : failure('PLATFORM_EXECUTION_FAILED', `child exited with ${exitCode}`);
    return { response, logs, childPid };
  }
  try {
    return { response: sanitize(JSON.parse(out.toString('utf8'))), logs, childPid };
  } catch {
    return { response: failure('INVALID_RESULT', 'the script wrote to standard output outside the contract'), logs, childPid };
  }
}

// Runs the script in `event` and returns the contract response.
export async function run(event, deadlineMs) {
  const workdir = mkdtempSync(path.join(TMP, 'run-'));
  let result = { response: failure('PLATFORM_EXECUTION_FAILED', 'wrapper error'), logs: Buffer.alloc(0), childPid: null };
  try {
    result = await execute(event ?? {}, workdir, deadlineMs);
  } catch (error) {
    // The instance must keep serving calls.
    result = { ...result, response: failure('PLATFORM_EXECUTION_FAILED', `wrapper error: ${error}`) };
  } finally {
    if (LOCAL) {
      if (result.childPid) {
        try { process.kill(-result.childPid, 'SIGKILL'); } catch { /* already gone */ }
      }
      rmSync(workdir, { recursive: true, force: true });
    } else {
      sweep();
      cleanTmp();
    }
  }
  const logs = result.logs.toString('utf8');
  if (logs) process.stderr.write(`[script] ${logs}\n`);
  return result.response;
}

