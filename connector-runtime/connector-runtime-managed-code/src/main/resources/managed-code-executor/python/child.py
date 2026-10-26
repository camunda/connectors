"""Runs user_script.py from the work directory and writes the contract response to stdout.

Started by executor_core in a fresh interpreter for every call. Adapted from the local wrapper of
POC 0.3, reading the request from stdin instead of request.json.
"""

import contextlib
import importlib.util
import json
import os
import sys
import traceback

CONTRACT_VERSION = "1"
MAX_MESSAGE_LENGTH = 1024
MAX_STACK_TRACE_LENGTH = 8192


def failure(code, error):
    stack_trace = traceback.format_exc()
    return {
        "contractVersion": CONTRACT_VERSION,
        "outcome": "FAILED",
        "error": {
            "code": code,
            "message": f"{type(error).__name__}: {error}"[:MAX_MESSAGE_LENGTH],
            "stackTrace": stack_trace[:MAX_STACK_TRACE_LENGTH],
            "retryable": False,
            "truncated": len(stack_trace) > MAX_STACK_TRACE_LENGTH,
        },
    }


def load(workdir):
    spec = importlib.util.spec_from_file_location("user_script", os.path.join(workdir, "user_script.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


workdir = sys.argv[1]
request = json.load(sys.stdin)
stdout = sys.stdout

try:
    if request.get("contractVersion") != CONTRACT_VERSION:
        raise TypeError("unsupported or missing contractVersion")
    variables = request.get("variables")
    context = request.get("context") or {}
    if not isinstance(variables, dict):
        raise TypeError("'variables' must be a JSON object")
    if not isinstance(context, dict):
        raise TypeError("'context' must be a JSON object")

    # print() in user code goes to the logs, not to the response.
    with contextlib.redirect_stdout(sys.stderr):
        result = load(workdir).execute(variables, context)
except MemoryError as error:
    response = failure("RESOURCE_LIMIT", error)
except BaseException as error:
    response = failure("SCRIPT_ERROR", error)
else:
    try:
        if not isinstance(result, dict):
            raise TypeError("execute() must return a JSON object")
        response = {"contractVersion": CONTRACT_VERSION, "outcome": "COMPLETED", "variables": result}
        json.dumps(response)
    except BaseException as error:
        response = failure("INVALID_RESULT", error)

stdout.write(json.dumps(response))
stdout.flush()
