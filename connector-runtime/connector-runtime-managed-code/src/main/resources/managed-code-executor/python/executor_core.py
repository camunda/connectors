"""Generic executor: runs one managed script per call, in a fresh child process.

Shared by the AWS Lambda, Cloud Run and local adapters. The script arrives as data in the request:
nothing is deployed per script. The wrapper is trusted; the child runs user code.

After every call the wrapper kills every process that was not there when the instance started
and removes every new entry in the temporary directory, so nothing survives into the next call.

With EXECUTOR_LOCAL=1, the local provider of a developer machine, the wrapper shares the host with
other processes: it kills only the process group of the script, and EXECUTOR_TMP is a directory of
its own for the call.

Ported from POC 0.11 of camunda/code-execution-poc.
"""

import ctypes
import json
import os
import resource
import shutil
import signal
import subprocess
import sys
import tempfile
import time

CONTRACT_VERSION = "1"
HERE = os.path.dirname(os.path.abspath(__file__))
CHILD = os.path.join(HERE, "child.py")
TMP = os.environ.get("EXECUTOR_TMP", "/tmp")
LOCAL = os.environ.get("EXECUTOR_LOCAL") == "1"
MAX_OUTPUT_BYTES = 1_000_000
MAX_LOG_BYTES = 4_096
CHILD_MEMORY_BYTES = 256 * 1024 * 1024
DEFAULT_TIMEOUT_MS = 10_000
SWEEP_ROUNDS = 5
PR_SET_DUMPABLE = 4



def _pids():
    try:
        return {int(entry) for entry in os.listdir("/proc") if entry.isdigit()}
    except FileNotFoundError:  # macOS: no /proc, only the process group is killed
        return set()


def _zombie(pid):
    try:
        with open(f"/proc/{pid}/stat", encoding="utf-8") as stat:
            return stat.read().rsplit(")", 1)[1].split()[0] == "Z"
    except (OSError, IndexError):
        return True  # gone


def _reap():
    """Reaps exited children. Matters when the wrapper is PID 1 (a container) and inherits orphans."""
    while True:
        try:
            pid, _ = os.waitpid(-1, os.WNOHANG)
        except ChildProcessError:
            return
        if pid == 0:
            return


def _ancestors():
    seen, pid = set(), os.getpid()
    while pid > 0 and pid not in seen:
        seen.add(pid)
        try:
            with open(f"/proc/{pid}/stat", encoding="utf-8") as stat:
                # The command name is in parentheses and may contain spaces: read after the last one.
                pid = int(stat.read().rsplit(")", 1)[1].split()[1])
        except (OSError, ValueError, IndexError):
            break
    return seen


def _entries(directory):
    try:
        return set(os.listdir(directory))
    except FileNotFoundError:
        return set()


SCRUBBED_VARIABLES = (b"AWS_ACCESS_KEY_ID=", b"AWS_SECRET_ACCESS_KEY=", b"AWS_SESSION_TOKEN=")


def _scrub_environment_block():
    """Overwrites the credentials in this process's initial environment block, which is what
    /proc/<pid>/environ shows to other processes of the same user.

    The wrapper never calls a cloud API, so it does not need them. prctl(PR_SET_DUMPABLE, 0) would
    hide the whole file, but Lambda rejects it with EPERM.
    """
    if not sys.platform.startswith("linux"):
        return {"error": "not Linux"}
    try:
        with open("/proc/self/stat", encoding="utf-8") as stat:
            fields = stat.read().rsplit(")", 1)[1].split()
        start, end = int(fields[47]), int(fields[48])  # fields 50 and 51: env_start, env_end
        with open("/proc/self/mem", "r+b", buffering=0) as mem:
            mem.seek(start)
            block = bytearray(mem.read(end - start))
            scrubbed, offset = 0, 0
            for entry in bytes(block).split(b"\0"):
                if entry.startswith(SCRUBBED_VARIABLES):
                    block[offset:offset + len(entry)] = bytes(len(entry))
                    scrubbed += 1
                offset += len(entry) + 1
            mem.seek(start)
            mem.write(bytes(block))
        for name in SCRUBBED_VARIABLES:
            os.environ.pop(name[:-1].decode(), None)
        return {"scrubbed": scrubbed}
    except (OSError, ValueError, IndexError) as error:
        return {"error": f"{type(error).__name__}: {error}"}


BASELINE_PIDS = _pids()
BASELINE_TMP = _entries(TMP)
if not LOCAL:
    _scrub_environment_block()


def _harden():
    """Asks the kernel to hide this process's /proc entries, including /proc/<pid>/environ, from
    other processes of the same user. Lambda rejects it with EPERM; Cloud Run allows it.
    """
    if not sys.platform.startswith("linux"):
        return
    try:
        ctypes.CDLL(None, use_errno=True).prctl(PR_SET_DUMPABLE, 0, 0, 0, 0)
    except (OSError, AttributeError):
        pass


def _limit_memory():
    try:
        resource.setrlimit(resource.RLIMIT_AS, (CHILD_MEMORY_BYTES, CHILD_MEMORY_BYTES))
    except (ValueError, OSError):  # macOS does not support RLIMIT_AS
        pass


def _child_env(workdir):
    # No platform variables: AWS_* credentials and the Runtime API address stay in the wrapper.
    return {
        "PATH": os.environ.get("EXECUTOR_CHILD_PATH", "/usr/local/bin:/usr/bin:/bin"),
        "HOME": workdir,
        "TMPDIR": workdir,
        "LANG": "C.UTF-8",
    }


def _sweep():
    """Kills every process that is not part of the instance baseline or an ancestor of this one.

    A script can leave its process group with setsid() or a double fork, so killing the group is
    not enough. Repeats while new processes appear, to catch processes forked during the sweep.
    """
    killed = set()
    protected = BASELINE_PIDS | _ancestors()
    for _ in range(SWEEP_ROUNDS):
        _reap()
        targets = {pid for pid in _pids() - protected - killed if not _zombie(pid)}
        if not targets:
            break
        for pid in targets:
            try:
                os.kill(pid, signal.SIGKILL)
                killed.add(pid)
            except (ProcessLookupError, PermissionError):
                protected.add(pid)
        time.sleep(0.01)
    _reap()
    return len(killed)


def _clean_tmp():
    removed = 0
    for entry in _entries(TMP) - BASELINE_TMP:
        path = os.path.join(TMP, entry)
        try:
            if os.path.isdir(path) and not os.path.islink(path):
                shutil.rmtree(path, ignore_errors=True)
            else:
                os.unlink(path)
            removed += 1
        except OSError:
            pass
    return removed


def _kill_group(proc):
    try:
        os.killpg(proc.pid, signal.SIGKILL)
    except (ProcessLookupError, PermissionError):
        pass


def _read(path, limit):
    with open(path, "rb") as handle:
        return handle.read(limit)


def failure(code, message, retryable=False):
    return {
        "contractVersion": CONTRACT_VERSION,
        "outcome": "FAILED",
        "error": {"code": code, "message": message[:1024], "retryable": retryable, "truncated": False},
    }


def _sanitize(response):
    """Keeps only contract fields, so a script cannot forge platform metadata."""
    if not isinstance(response, dict) or response.get("outcome") not in ("COMPLETED", "FAILED"):
        return failure("INVALID_RESULT", "the child did not return a contract response")
    allowed = ("contractVersion", "outcome", "variables", "error")
    return {key: response[key] for key in allowed if key in response}


def _execute(event, workdir, deadline_ms):
    script = event.get("script")
    if not isinstance(script, str) or not script:
        return failure("INVALID_INPUT", "'script' must be a non-empty string"), b"", None
    with open(os.path.join(workdir, "user_script.py"), "w", encoding="utf-8") as handle:
        handle.write(script)
    request = {
        "contractVersion": event.get("contractVersion"),
        "variables": event.get("variables"),
        "context": event.get("context", {}),
    }
    timeout_ms = max(1, min(int(event.get("timeoutMs") or DEFAULT_TIMEOUT_MS), deadline_ms))

    # Output goes to files, not pipes: a leftover process holding a pipe open would block the read.
    stdout_path = os.path.join(workdir, ".stdout")
    stderr_path = os.path.join(workdir, ".stderr")
    with open(stdout_path, "wb") as stdout, open(stderr_path, "wb") as stderr:
        proc = subprocess.Popen(
            [sys.executable, "-I", CHILD, workdir],
            cwd=workdir,
            env=_child_env(workdir),
            stdin=subprocess.PIPE,
            stdout=stdout,
            stderr=stderr,
            start_new_session=True,
            preexec_fn=_limit_memory,
        )
        try:
            proc.stdin.write(json.dumps(request).encode("utf-8"))
            proc.stdin.close()
        except BrokenPipeError:
            pass
        try:
            proc.wait(timeout=timeout_ms / 1000)
        except subprocess.TimeoutExpired:
            _kill_group(proc)
            proc.wait()
            return failure("TIMEOUT", f"script exceeded {timeout_ms} ms"), _read(stderr_path, MAX_LOG_BYTES), proc

    out = _read(stdout_path, MAX_OUTPUT_BYTES + 1)
    logs = _read(stderr_path, MAX_LOG_BYTES)
    if len(out) > MAX_OUTPUT_BYTES:
        return failure("RESOURCE_LIMIT", f"output larger than {MAX_OUTPUT_BYTES} bytes"), logs, proc
    if not out:
        if b"MemoryError" in logs:
            return failure("RESOURCE_LIMIT", "script ran out of memory"), logs, proc
        return failure("PLATFORM_EXECUTION_FAILED", f"child exited with {proc.returncode}"), logs, proc
    try:
        return _sanitize(json.loads(out)), logs, proc
    except ValueError:
        return failure("INVALID_RESULT", "the script wrote to standard output outside the contract"), logs, proc


def run(event, deadline_ms):
    """Runs the script in ``event`` and returns the contract response."""
    _harden()
    workdir = tempfile.mkdtemp(prefix="run-", dir=TMP)
    response, logs, proc = failure("PLATFORM_EXECUTION_FAILED", "wrapper error"), b"", None
    try:
        response, logs, proc = _execute(event, workdir, deadline_ms)
    except Exception as error:  # noqa: BLE001 - the instance must keep serving calls
        response = failure("PLATFORM_EXECUTION_FAILED", f"wrapper error: {type(error).__name__}: {error}")
    finally:
        if LOCAL:
            if proc is not None:
                _kill_group(proc)
            shutil.rmtree(workdir, ignore_errors=True)
        else:
            _sweep()
            _clean_tmp()
    text = logs.decode("utf-8", errors="replace")
    if text:
        sys.stderr.write("[script] " + text + "\n")
    return response
