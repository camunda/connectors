# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
# under one or more contributor license agreements. Licensed under a proprietary license.
# See the License.txt file for more information. You may not use this file
# except in compliance with the proprietary license.
"""AWS Lambda entry point of the Python executor. The executor is created once per instance, when
Lambda loads this module, so that what exists at that moment is kept after every call and anything
else is removed.
"""

import executor_core

# Left to the wrapper, out of the remaining time of the function, to answer and clean up.
SAFETY_MS = 1_000

executor = executor_core.create_executor(local=False, tmp="/tmp")


def handler(event, context):
    return executor.run(event, context.get_remaining_time_in_millis() - SAFETY_MS)
