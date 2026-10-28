"""AWS Lambda adapter of the generic Python executor."""

import executor_core

SAFETY_MS = 1_000


def handler(event, context):
    return executor_core.run(event, context.get_remaining_time_in_millis() - SAFETY_MS)
