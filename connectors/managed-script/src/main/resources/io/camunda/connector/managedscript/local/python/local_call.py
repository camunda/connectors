# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
# under one or more contributor license agreements. Licensed under a proprietary license.
# See the License.txt file for more information. You may not use this file
# except in compliance with the proprietary license.
"""Local entry point of the Python executor: runs one call, read from stdin, and writes the contract
response to stdout. Always in local mode, so it only ever kills the process group of its own script.
Every call gets a directory of its own under EXECUTOR_TMP, or the system's temporary directory, and
the directory is removed after the call.
"""

import json
import os
import sys
import tempfile

# No __pycache__ next to the wrapper files, whose content on disk is checked against the digest.
sys.dont_write_bytecode = True
# Started with -I, which leaves the script directory off the path.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import executor_core  # noqa: E402

# No platform deadline on a local host: the request's timeoutMs alone bounds the script.
DEADLINE_MS = float(os.environ.get("EXECUTOR_DEADLINE_MS") or "inf")

executor = executor_core.create_executor(local=True, tmp=os.environ.get("EXECUTOR_TMP") or tempfile.gettempdir())

try:
    event = json.loads(sys.stdin.buffer.read().decode("utf-8"))
except ValueError:
    event = None
sys.stdout.write(json.dumps(executor.run(event, DEADLINE_MS)))
sys.stdout.flush()
