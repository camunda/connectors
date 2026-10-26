"""Cloud Run functions adapter of the generic Python executor.

Cloud Run gives no remaining-time API, so the deadline comes from the function timeout, passed by
the deployer in EXECUTOR_FUNCTION_TIMEOUT_MS. Requires a request concurrency of 1: the sweep after
a call kills every process it did not start with.
"""

import os

import functions_framework

import executor_core

FUNCTION_TIMEOUT_MS = int(os.environ.get("EXECUTOR_FUNCTION_TIMEOUT_MS", "60000"))
SAFETY_MS = 1_000


@functions_framework.http
def executor(request):
    return executor_core.run(request.get_json(silent=True) or {}, FUNCTION_TIMEOUT_MS - SAFETY_MS)
