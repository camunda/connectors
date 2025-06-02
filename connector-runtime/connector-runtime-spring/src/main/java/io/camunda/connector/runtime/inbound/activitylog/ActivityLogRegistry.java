package io.camunda.connector.runtime.inbound.activitylog;

import com.google.common.collect.EvictingQueue;
import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogEntry;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class ActivityLogRegistry implements ActivityLogWriter {

  private final Logger LOG = LoggerFactory.getLogger(ActivityLogRegistry.class);

  private final Map<String, EvictingQueue<Activity>> activityLogs = new HashMap<>();

  public Queue<Activity> getLogs(String executableId) {
    return activityLogs.get(executableId);
  }

  @Override
  public void log(ActivityLogEntry logEntry) {
    String message = logEntry.activity().toString();
    MDC.put("executableId", logEntry.executableId());
    switch (logEntry.activity().severity()) {
      case DEBUG -> LOG.debug(message);
      case INFO -> LOG.info(message);
      case ERROR, WARNING -> LOG.warn(message); // errors would be too noisy
    }
    MDC.clear();
    activityLogs.compute(
        logEntry.executableId(),
        (key, value) -> {
          if (value == null) {
            EvictingQueue<Activity> newQueue = EvictingQueue.create(100); // TODO: externalize
            newQueue.add(logEntry.activity());
            return newQueue;
          }
          value.add(logEntry.activity());
          return value;
        }
    );
  }
}
