# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more
# contributor license agreements. Licensed under the Apache License, Version 2.0.
"""Generic executor: runs one managed script per call, in a fresh child process (ADR-0008).

Shared by every provider's adapter. The script arrives as data in the request: nothing is deployed
per script. The wrapper is trusted; the child runs user code.

On a cloud executor, after every call the wrapper kills every process that was not there when the
executor was created and removes every new entry in the temporary directory, so nothing survives
into the next call. In local mode, on a host shared with other processes, it kills only the process
group of the script and removes only the directory of the call.

Runs on Python 3.10 or later.
"""

import ctypes
import json
import math
import os

try:
    import resource
except ImportError:  # Windows: no rlimits; the local mode is not supported there
    resource = None
import signal
import stat
import subprocess
import sys
import tempfile
import time

CONTRACT_VERSION = "1"
CHILD = os.path.join(os.path.dirname(os.path.abspath(__file__)), "child.py")
MAX_OUTPUT_BYTES = 1_000_000
MAX_LOG_BYTES = 4_096
MAX_MESSAGE_LENGTH = 1024
MAX_STACK_TRACE_LENGTH = 8192
CHILD_MEMORY_BYTES = 256 * 1024 * 1024
DEFAULT_TIMEOUT_MS = 10_000
SWEEP_ROUNDS = 5
PR_SET_DUMPABLE = 4
# The only failures the child may report: the others are the platform's.
CHILD_FAILURE_CODES = ("SCRIPT_ERROR", "INVALID_RESULT", "RESOURCE_LIMIT")
SCRUBBED_VARIABLES = (b"AWS_ACCESS_KEY_ID=", b"AWS_SECRET_ACCESS_KEY=", b"AWS_SESSION_TOKEN=")


def _pids():
    try:
        return {int(entry) for entry in os.listdir("/proc") if entry.isdigit()}
    except FileNotFoundError:  # macOS: no /proc, only the process group is killed
        return set()


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


def _allow_owner(path):
    """Gives the owner the permissions on a directory. Never follows a link: chmod would."""
    try:
        mode = os.lstat(path).st_mode
        if stat.S_ISDIR(mode):
            os.chmod(path, mode | 0o700)
    except OSError:
        pass


def _tree(directory, repair=False):
    """Every path under ``directory``, relative to it, with its mode, without following links.

    With ``repair``, the owner gets the permissions back on every directory before it is read: a
    script can take them off a directory, and what it left inside would go unseen.
    """
    found, pending = {}, [""]
    while pending:
        relative = pending.pop()
        path = os.path.join(directory, relative)
        if repair and relative:
            _allow_owner(path)
        try:
            names = os.listdir(path)
        except OSError:
            continue
        for name in names:
            child = os.path.join(relative, name)
            try:
                mode = os.lstat(os.path.join(directory, child)).st_mode
            except OSError:
                continue
            found[child] = mode
            if stat.S_ISDIR(mode):
                pending.append(child)
    return found


def _scrub_environment_block():
    """Overwrites the credentials in this process's initial environment block, which is what
    /proc/<pid>/environ shows to other processes of the same user.

    The wrapper never calls a cloud API, so it does not need them. prctl(PR_SET_DUMPABLE, 0) hides
    the whole file where the platform allows it, but Lambda rejects it with EPERM.
    """
    for name in SCRUBBED_VARIABLES:
        os.environ.pop(name[:-1].decode(), None)
    if not sys.platform.startswith("linux"):
        return
    try:
        with open("/proc/self/stat", encoding="utf-8") as stat:
            fields = stat.read().rsplit(")", 1)[1].split()
        start, end = int(fields[47]), int(fields[48])  # fields 50 and 51: env_start, env_end
        with open("/proc/self/mem", "r+b", buffering=0) as mem:
            mem.seek(start)
            block = bytearray(mem.read(end - start))
            offset = 0
            for entry in bytes(block).split(b"\0"):
                if entry.startswith(SCRUBBED_VARIABLES):
                    block[offset:offset + len(entry)] = bytes(len(entry))
                offset += len(entry) + 1
            mem.seek(start)
            mem.write(bytes(block))
    except (OSError, ValueError, IndexError):
        pass  # Best effort: the child never inherits this environment anyway.


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
    """Runs in the child before exec: a script that allocates more gets a MemoryError."""
    if resource is None:
        return
    try:
        resource.setrlimit(resource.RLIMIT_AS, (CHILD_MEMORY_BYTES, CHILD_MEMORY_BYTES))
    except (ValueError, OSError):  # macOS does not support RLIMIT_AS
        pass


def _remove(path):
    """Removes a file, a link or a whole directory tree, and never follows a link.

    A script can take the permissions off a directory, so that its content cannot be listed or
    removed: the owner gets them back on every directory of the tree before it is read.
    """
    directories, pending = [], [path]
    while pending:
        current = pending.pop()
        try:
            is_directory = stat.S_ISDIR(os.lstat(current).st_mode)
        except OSError:
            continue
        if not is_directory:
            try:
                os.unlink(current)
            except OSError:
                pass
            continue
        _allow_owner(current)
        try:
            pending.extend(os.path.join(current, name) for name in os.listdir(current))
        except OSError:
            pass
        directories.append(current)
    for directory in reversed(directories):  # the deepest first
        try:
            os.rmdir(directory)
        except OSError:
            pass


def _kill_group(pid):
    try:
        os.killpg(pid, signal.SIGKILL)
    except OSError:
        pass  # already gone


def _read(fd, limit):
    return os.pread(fd, limit, 0)


def _reject_constant(name):
    raise ValueError(f"{name} is not JSON")


def _finite(text):
    number = float(text)
    if not math.isfinite(number):
        raise ValueError(f"{text} is out of range")
    return number


def failure(code, message):
    return {
        "contractVersion": CONTRACT_VERSION,
        "outcome": "FAILED",
        "error": {"code": code, "message": message[:MAX_MESSAGE_LENGTH], "retryable": False, "truncated": False},
    }


def _sanitize(response):
    """The child is not trusted: keeps only contract fields, so a script cannot forge a platform
    failure or ask to be retried.
    """
    if not isinstance(response, dict):
        return failure("INVALID_RESULT", "the child did not return a contract response")
    if response.get("outcome") == "COMPLETED" and isinstance(response.get("variables"), dict):
        return {"contractVersion": CONTRACT_VERSION, "outcome": "COMPLETED", "variables": response["variables"]}
    error = response.get("error")
    if response.get("outcome") == "FAILED" and isinstance(error, dict) and error.get("code") in CHILD_FAILURE_CODES:
        message = error.get("message")
        sanitized = {
            "code": error["code"],
            "message": ("" if message is None else str(message))[:MAX_MESSAGE_LENGTH],
            "retryable": False,
        }
        truncated = error.get("truncated") is True
        if isinstance(error.get("stackTrace"), str):
            # The end of a Python traceback is the frame nearest the error.
            sanitized["stackTrace"] = error["stackTrace"][-MAX_STACK_TRACE_LENGTH:]
            truncated = truncated or len(error["stackTrace"]) > MAX_STACK_TRACE_LENGTH
        sanitized["truncated"] = truncated
        return {"contractVersion": CONTRACT_VERSION, "outcome": "FAILED", "error": sanitized}
    return failure("INVALID_RESULT", "the child did not return a contract response")


def _child_env(workdir):
    # No platform variables: credentials and the platform's addresses stay in the wrapper.
    return {
        "PATH": os.environ.get("EXECUTOR_CHILD_PATH", "/usr/local/bin:/usr/bin:/bin"),
        "HOME": workdir,
        "TMPDIR": workdir,
        "LANG": "C.UTF-8",
    }


def _validate(event):
    if event.get("contractVersion") != CONTRACT_VERSION:
        return failure(
            "UNSUPPORTED_CONTRACT_VERSION",
            f"contract version {json.dumps(event.get('contractVersion'))} is not supported, "
            f"expected {CONTRACT_VERSION}",
        )
    script = event.get("script")
    if not isinstance(script, str) or not script:
        return failure("INVALID_INPUT", "'script' must be a non-empty string")
    if not isinstance(event.get("variables"), dict):
        return failure("INVALID_INPUT", "'variables' must be a JSON object")
    if event.get("context") is not None and not isinstance(event["context"], dict):
        return failure("INVALID_INPUT", "'context' must be a JSON object")
    return None


def _timeout_ms(event, deadline_ms):
    try:
        requested = float(event.get("timeoutMs"))
    except (TypeError, ValueError):
        requested = 0
    if not math.isfinite(requested) or requested <= 0:
        requested = DEFAULT_TIMEOUT_MS
    return max(1, int(min(requested, deadline_ms)))


def _execute(event, workdir, deadline_ms):
    """Returns the response, the script's logs and the pid of the child, or None."""
    with open(os.path.join(workdir, "user_script.py"), "w", encoding="utf-8") as handle:
        handle.write(event["script"])
    request = {"variables": event["variables"], "context": event.get("context") or {}}
    timeout_ms = _timeout_ms(event, deadline_ms)

    # Output goes to files, not pipes: a leftover process holding a pipe open would block the read.
    # They are read through the descriptors opened here, never reopened by name, so a script that
    # replaces them, with a FIFO for example, cannot block the wrapper.
    flags = os.O_RDWR | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
    stdout = os.open(os.path.join(workdir, ".stdout"), flags, 0o600)
    try:
        stderr = os.open(os.path.join(workdir, ".stderr"), flags, 0o600)
    except OSError:
        os.close(stdout)
        raise
    try:
        child = subprocess.Popen(
            # -I: no PYTHON* variables, no user site directory, no work directory on the path.
            [sys.executable, "-I", CHILD, workdir],
            cwd=workdir,
            env=_child_env(workdir),
            stdin=subprocess.PIPE,
            stdout=stdout,
            stderr=stderr,
            start_new_session=True,  # own process group, killed as a whole on timeout
            preexec_fn=_limit_memory,
        )
        try:
            child.stdin.write(json.dumps(request).encode("utf-8"))
            child.stdin.close()
        except BrokenPipeError:
            pass
        try:
            exit_code = child.wait(timeout=timeout_ms / 1000)
        except subprocess.TimeoutExpired:
            _kill_group(child.pid)
            child.wait()
            return failure("TIMEOUT", f"script exceeded {timeout_ms} ms"), _read(stderr, MAX_LOG_BYTES), child.pid
        # Before anything is read, so that nothing the script left running survives a failure below.
        _kill_group(child.pid)

        logs = _read(stderr, MAX_LOG_BYTES)
        out = _read(stdout, MAX_OUTPUT_BYTES + 1)
    finally:
        os.close(stdout)
        os.close(stderr)
    if len(out) > MAX_OUTPUT_BYTES:
        return failure("RESOURCE_LIMIT", f"output larger than {MAX_OUTPUT_BYTES} bytes"), logs, child.pid
    if not out:
        if b"MemoryError" in logs:
            return failure("RESOURCE_LIMIT", "script ran out of memory"), logs, child.pid
        return failure("PLATFORM_EXECUTION_FAILED", f"child exited with {exit_code}"), logs, child.pid
    try:
        # A script can write a response to fd 1 itself: NaN, Infinity and 1e999 are not JSON.
        response = json.loads(out, parse_constant=_reject_constant, parse_float=_finite)
        return _sanitize(response), logs, child.pid
    except (ValueError, RecursionError):  # RecursionError: nesting deeper than the parser allows
        return failure("INVALID_RESULT", "the script wrote to standard output outside the contract"), logs, child.pid


class Executor:
    """The executor of one instance: see create_executor."""

    def __init__(self, local, tmp):
        self._local = local
        self._tmp = tmp
        self._baseline_pids = None if local else _pids()
        self._baseline_tmp = None if local else _tree(tmp)
        if not local:
            _scrub_environment_block()
            _harden()

    def _sweep(self):
        """Kills every process that is not part of the baseline or an ancestor of this one.

        A script can leave its process group with setsid() or a double fork, so killing the group is
        not enough. Repeats while new processes appear, to catch processes forked during the sweep.
        """
        killed = set()
        protected = self._baseline_pids | _ancestors()
        for _ in range(SWEEP_ROUNDS):
            _reap()
            targets = {pid for pid in _pids() - protected - killed if not _zombie(pid)}
            if not targets:
                break
            for pid in targets:
                try:
                    os.kill(pid, signal.SIGKILL)
                    killed.add(pid)
                except OSError:
                    protected.add(pid)
            time.sleep(0.01)
        _reap()

    def _clean_tmp(self):
        # Everything new under the directory, however deep: a script can write inside a directory
        # that was already there. Shallowest first, so a new directory goes in one call.
        new = _tree(self._tmp, repair=True).keys() - self._baseline_tmp.keys()
        for entry in sorted(new, key=lambda found: found.count(os.sep)):
            path = os.path.join(self._tmp, entry)
            if os.path.lexists(path):
                _remove(path)
        # The directories of the baseline had their owner permissions restored to be read: they get
        # the mode they had when the executor was created.
        for entry, mode in self._baseline_tmp.items():
            path = os.path.join(self._tmp, entry)
            try:
                if stat.S_ISDIR(mode) and stat.S_ISDIR(os.lstat(path).st_mode):
                    os.chmod(path, stat.S_IMODE(mode))
            except OSError:
                pass

    def run(self, event, deadline_ms):
        """Runs the script of ``event`` and returns the contract response; never raises."""
        if not isinstance(event, dict):
            event = {}
        invalid = _validate(event)
        if invalid:
            return invalid
        workdir = tempfile.mkdtemp(prefix="run-", dir=self._tmp)
        response, logs, child_pid = failure("PLATFORM_EXECUTION_FAILED", "wrapper error"), b"", None
        try:
            response, logs, child_pid = _execute(event, workdir, deadline_ms)
        except Exception as error:  # noqa: BLE001 - the executor must keep serving calls
            response = failure("PLATFORM_EXECUTION_FAILED", f"wrapper error: {type(error).__name__}: {error}")
        finally:
            if self._local:
                if child_pid is not None:
                    _kill_group(child_pid)
                _remove(workdir)
            else:
                self._sweep()
                self._clean_tmp()
        text = logs.decode("utf-8", errors="replace")
        if text:
            sys.stderr.write(f"[script] {text}\n")
        return response


def create_executor(local, tmp):
    """Creates the executor of one instance. Call it once, when the instance starts: on a cloud
    executor, what exists at that moment is kept after every call, and anything else is removed.

    :param local: True for a host shared with other processes
    :param tmp: the directory where every call gets a directory of its own
    """
    return Executor(local, tmp)
