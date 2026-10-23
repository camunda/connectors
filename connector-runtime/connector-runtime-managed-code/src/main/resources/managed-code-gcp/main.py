import contextlib
import importlib
import json
import sys
import traceback

import functions_framework

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
            "message": str(error)[:MAX_MESSAGE_LENGTH],
            "stackTrace": stack_trace[:MAX_STACK_TRACE_LENGTH],
            "retryable": False,
            "truncated": len(stack_trace) > MAX_STACK_TRACE_LENGTH,
        },
    }


def handle(request):
    try:
        if not isinstance(request, dict) or request.get("contractVersion") != CONTRACT_VERSION:
            raise TypeError("unsupported or missing contractVersion")
        variables = request.get("variables")
        context = request.get("context", {})
        if not isinstance(variables, dict):
            raise TypeError("'variables' must be a JSON object")
        if not isinstance(context, dict):
            raise TypeError("'context' must be a JSON object")

        # The user script is imported here, not at module load, so that a syntax error is reported
        # as a script failure instead of a failed function start.
        with contextlib.redirect_stdout(sys.stderr):
            user_script = importlib.import_module("user_script")
            execute = getattr(user_script, "execute")
            result = execute(variables, context)
    except BaseException as error:
        return failure("SCRIPT_ERROR", error)

    try:
        if not isinstance(result, dict):
            raise TypeError("execute() must return a JSON object")
        response = {
            "contractVersion": CONTRACT_VERSION,
            "outcome": "COMPLETED",
            "variables": result,
        }
        json.dumps(response)
        return response
    except BaseException as error:
        return failure("INVALID_RESULT", error)


@functions_framework.http
def managed_script(http_request):
    response = handle(http_request.get_json(silent=True))
    return json.dumps(response), 200, {"Content-Type": "application/json"}
