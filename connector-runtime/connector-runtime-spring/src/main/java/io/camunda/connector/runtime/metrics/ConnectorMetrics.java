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
package io.camunda.connector.runtime.metrics;

import io.camunda.spring.client.metrics.MetricsRecorder;

public class ConnectorMetrics {

  public static class Outbound {

    public static final String METRIC_NAME_INVOCATIONS = "camunda.connector.outbound.invocations";
    public static final String METRIC_NAME_TIME = "camunda.connector.outbound.execution-time";

    public static final String JOB_RECEIVED = "outbound-connector-job-received";
    // same as job worker metrics from Spring Zeebe
    public static final String ACTION_ACTIVATED = MetricsRecorder.ACTION_ACTIVATED;
    public static final String ACTION_COMPLETED = MetricsRecorder.ACTION_COMPLETED;
    public static final String ACTION_FAILED = MetricsRecorder.ACTION_FAILED;
  }

  public static class Inbound {
    public static final String METRIC_NAME_INBOUND_PROCESS_DEFINITIONS_CHECKED =
        "camunda.connector.inbound.process-definitions-checked";
  }
}
