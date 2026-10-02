import contextlib
import importlib
import json
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
            "message": str(error)[:MAX_MESSAGE_LENGTH],
            "stackTrace": stack_trace[:MAX_STACK_TRACE_LENGTH],
            "retryable": False,
            "truncated": len(stack_trace) > MAX_STACK_TRACE_LENGTH,
        },
    }


with open("request.json", encoding="utf-8") as request_file:
    request = json.load(request_file)

try:
    if request.get("contractVersion") != CONTRACT_VERSION:
        raise TypeError("unsupported or missing contractVersion")
    variables = request.get("variables")
    context = request.get("context", {})
    if not isinstance(variables, dict):
        raise TypeError("'variables' must be a JSON object")
    if not isinstance(context, dict):
        raise TypeError("'context' must be a JSON object")

    with contextlib.redirect_stdout(sys.stderr):
        user_script = importlib.import_module("user_script")
        execute = getattr(user_script, "execute")
        result = execute(variables, context)
except BaseException as error:
    response = failure("SCRIPT_ERROR", error)
else:
    try:
        if not isinstance(result, dict):
            raise TypeError("execute() must return a JSON object")
        response = {
            "contractVersion": CONTRACT_VERSION,
            "outcome": "COMPLETED",
            "variables": result,
        }
        json.dumps(response)
    except BaseException as error:
        response = failure("INVALID_RESULT", error)

sys.stdout.write(json.dumps(response))
