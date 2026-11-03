# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
# under one or more contributor license agreements. Licensed under a proprietary license.
# See the License.txt file for more information. You may not use this file
# except in compliance with the proprietary license.
"""Cloud Run functions entry point of the Python executor. The executor is created once per
instance, when the Functions Framework loads this module, so that what exists at that moment is
kept after every call and anything else is removed. The function serves one request at a time: the
cleanup after a call would kill a concurrent script.
"""

import os

import functions_framework

import executor_core

# Left to the wrapper, out of the function timeout, to answer and clean up.
SAFETY_MS = 1_000

# Cloud Run gives no remaining time: the provider sets the function timeout.
FUNCTION_TIMEOUT_MS = int(os.environ["EXECUTOR_FUNCTION_TIMEOUT_MS"])

TMP = os.environ.get("EXECUTOR_TMP", "/tmp")

# An instance keeps /tmp when its server restarts after a timeout or a crash: what a call left there
# must not become part of what the executor keeps. Better not to start than to keep it.
_left = executor_core.clear(TMP)
if _left:
    raise RuntimeError("Cannot clear %s of a previous server: %s" % (TMP, ", ".join(_left)))

executor = executor_core.create_executor(local=False, tmp=TMP)


@functions_framework.http
def handler(request):
    return executor.run(request.get_json(silent=True), FUNCTION_TIMEOUT_MS - SAFETY_MS)
