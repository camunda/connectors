package io.camunda.connector.runtime.core.inbound.activitylog;

public interface ActivityLogWriter {
  void log(ActivityLogEntry logEntry);
}
