"""Local adapter of the Python executor: runs one call, read from stdin, and writes the response.

Started by the local provider for every call with EXECUTOR_LOCAL=1, so the wrapper only kills the
process group of the script, and EXECUTOR_TMP is a directory of its own for the call.
"""

import json
import os
import sys

# Started with -I, which leaves the script directory off the path.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import executor_core  # noqa: E402

DEADLINE_MS = int(os.environ.get("EXECUTOR_DEADLINE_MS", "30000"))

sys.stdout.write(json.dumps(executor_core.run(json.load(sys.stdin), DEADLINE_MS)))
sys.stdout.flush()
