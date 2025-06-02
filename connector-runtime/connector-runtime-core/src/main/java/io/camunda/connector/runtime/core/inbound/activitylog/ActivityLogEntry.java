package io.camunda.connector.runtime.core.inbound.activitylog;

import io.camunda.connector.api.inbound.Activity;

public record ActivityLogEntry(
    String executableId,
    Activity activity,
    ActivitySource source
) {}
