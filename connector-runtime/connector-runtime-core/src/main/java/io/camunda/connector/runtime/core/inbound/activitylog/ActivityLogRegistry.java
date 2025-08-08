/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.core.inbound.activitylog;

import com.google.common.collect.EvictingQueue;
import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class ActivityLogRegistry implements ActivityLogWriter {

  private final Logger LOG = LoggerFactory.getLogger(ActivityLogRegistry.class);

  private final Map<String, EvictingQueue<Activity>> activityLogs = new HashMap<>();

  private final int maxLogSize;

  public ActivityLogRegistry(int maxLogSize) {
    this.maxLogSize = maxLogSize;
  }

  public ActivityLogRegistry() {
    this(100); // Default size, can be overridden
  }

  public Queue<Activity> getLogs(ExecutableId executableId) {
    return activityLogs.get(executableId.getId());
  }

  @Override
  public void log(ActivityLogEntry logEntry) {
    String message = logEntry.activity().toString();
    MDC.put("executableId", logEntry.executableId().getId());
    switch (logEntry.activity().severity()) {
      case DEBUG -> LOG.debug(message);
      case INFO -> LOG.info(message);
      case ERROR, WARNING -> LOG.warn(message); // errors would be too noisy
    }
    MDC.clear();
    activityLogs.compute(
        logEntry.executableId().getId(),
        (key, value) -> {
          if (value == null) {
            EvictingQueue<Activity> newQueue = EvictingQueue.create(maxLogSize);
            newQueue.add(logEntry.activity());
            return newQueue;
          }
          value.add(logEntry.activity());
          return value;
        });
  }
}
