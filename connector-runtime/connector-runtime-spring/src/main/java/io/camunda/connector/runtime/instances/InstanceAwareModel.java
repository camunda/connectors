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
package io.camunda.connector.runtime.instances;

import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.Severity;
import java.time.OffsetDateTime;
import java.util.Map;

public sealed interface InstanceAwareModel
    permits InstanceAwareModel.InstanceAwareActivity, InstanceAwareModel.InstanceAwareHealth {
  record InstanceAwareActivity(
      Severity severity, String tag, OffsetDateTime timestamp, String message, String runtimeId)
      implements InstanceAwareModel {}

  record InstanceAwareHealth(
      Health.Status status, Health.Error error, Map<String, Object> details, String runtimeId)
      implements InstanceAwareModel {}
}
