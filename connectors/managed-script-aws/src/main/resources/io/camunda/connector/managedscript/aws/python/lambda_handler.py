# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
# under one or more contributor license agreements. Licensed under a proprietary license.
# See the License.txt file for more information. You may not use this file
# except in compliance with the proprietary license.
"""AWS Lambda entry point of the Python executor. The executor is created once per instance, when
Lambda loads this module, so that what exists at that moment is kept after every call and anything
else is removed.
"""

import os
import shutil

import executor_core

# Left to the wrapper, out of the remaining time of the function, to answer and clean up.
SAFETY_MS = 1_000

TMP = os.environ.get("EXECUTOR_TMP", "/tmp")


def _clear(directory):
    """Lambda keeps /tmp when it restarts the runtime after a timeout or a crash: what a call left
    there must not become part of what the executor keeps."""
    for entry in os.scandir(directory):
        if entry.is_dir(follow_symlinks=False):
            shutil.rmtree(entry.path, ignore_errors=True)
        else:
            try:
                os.unlink(entry.path)
            except OSError:
                pass


_clear(TMP)
executor = executor_core.create_executor(local=False, tmp=TMP)


def handler(event, context):
    return executor.run(event, context.get_remaining_time_in_millis() - SAFETY_MS)
