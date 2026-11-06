# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more
# contributor license agreements. Licensed under the Apache License, Version 2.0.
"""Runs user_script.py from the work directory and writes the contract response to stdout.

Started by executor_core in a fresh interpreter for every call, with a request that executor_core
has already validated: { variables, context }.
"""

import importlib.util
import json
import os
import sys
import traceback

CONTRACT_VERSION = "1"
MAX_MESSAGE_LENGTH = 1024
MAX_STACK_TRACE_LENGTH = 8192


def failure(code, message, stack_trace=None):
    error = {"code": code, "message": message[:MAX_MESSAGE_LENGTH], "retryable": False, "truncated": False}
    if stack_trace is not None:
        # The end of a Python traceback is the frame nearest the error.
        error["stackTrace"] = stack_trace[-MAX_STACK_TRACE_LENGTH:]
        error["truncated"] = len(stack_trace) > MAX_STACK_TRACE_LENGTH
    return {"contractVersion": CONTRACT_VERSION, "outcome": "FAILED", "error": error}


def describe(error):
    """The text of an error. The script's own __str__ runs here, and it can raise too."""
    try:
        return f"{type(error).__name__}: {error}"
    except BaseException:  # noqa: BLE001
        return type(error).__name__


def raised(code, error):
    try:
        stack_trace = traceback.format_exc()
    except BaseException:  # noqa: BLE001 - the traceback formats the exception with the same __str__
        stack_trace = None
    return failure(code, describe(error), stack_trace)


def load(workdir):
    spec = importlib.util.spec_from_file_location("user_script", os.path.join(workdir, "user_script.py"))
    module = importlib.util.module_from_spec(spec)
    # Registered like an imported module: dataclasses and pickle look the module up by name.
    sys.modules["user_script"] = module
    spec.loader.exec_module(module)
    execute = getattr(module, "execute", None)
    if not callable(execute):
        raise TypeError("the script must define a function execute(variables, context)")
    return execute


request = json.loads(sys.stdin.buffer.read().decode("utf-8"))
stdout = sys.stdout
# print() in user code goes to the logs, not to the response. Redirected for the whole life of the
# process: a thread the script left running must not write to the response after execute() returns.
sys.stdout = sys.stderr

try:
    result = load(sys.argv[1])(request["variables"], request["context"])
except MemoryError as error:
    response = raised("RESOURCE_LIMIT", error)
except BaseException as error:  # noqa: BLE001 - SystemExit and KeyboardInterrupt are script errors too
    response = raised("SCRIPT_ERROR", error)
else:
    if not isinstance(result, dict):
        response = failure("INVALID_RESULT", "execute() must return a JSON object")
    else:
        try:
            # allow_nan=False: NaN and Infinity are not JSON.
            response = json.dumps(
                {"contractVersion": CONTRACT_VERSION, "outcome": "COMPLETED", "variables": result}, allow_nan=False
            )
        except MemoryError as error:
            response = raised("RESOURCE_LIMIT", error)
        except BaseException as error:  # noqa: BLE001 - json.dumps runs the result's own methods, which can raise anything
            response = failure("INVALID_RESULT", f"the result of execute() is not JSON: {describe(error)}")

stdout.write(response if isinstance(response, str) else json.dumps(response))
stdout.flush()
sys.stderr.flush()
# Exit once the response is written: a thread the script left running must not turn a finished call
# into a timeout, nor write to stdout after the response.
os._exit(0)
