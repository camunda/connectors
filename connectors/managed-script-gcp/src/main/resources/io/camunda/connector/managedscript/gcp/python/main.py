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
import sys

import functions_framework

import executor_core

# Left to the wrapper, out of the function timeout, to answer and clean up.
SAFETY_MS = 1_000

# Cloud Run gives no remaining time: the provider sets the function timeout.
FUNCTION_TIMEOUT_MS = int(os.environ["EXECUTOR_FUNCTION_TIMEOUT_MS"])

# The memory of the instance, set by the provider: the script gets it but for a margin.
MEMORY_MB = int(os.environ["EXECUTOR_MEMORY_MB"])

TMP = os.environ.get("EXECUTOR_TMP", "/tmp")

# Whatever is in /tmp when the module loads becomes part of what the executor keeps. A new container
# starts with an empty /tmp, but for entries of the platform, such as "run", that cannot be removed:
# they are kept. Anything a previous server of the instance left that can be removed is removed.
_left = executor_core.clear(TMP)
if _left:
    print("Kept in %s, which the server cannot remove: %s" % (TMP, ", ".join(_left)), file=sys.stderr)

executor = executor_core.create_executor(local=False, tmp=TMP, memory_mb=MEMORY_MB)


@functions_framework.http
def handler(request):
    return executor.run(request.get_json(silent=True), FUNCTION_TIMEOUT_MS - SAFETY_MS)
