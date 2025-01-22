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
package io.camunda.connector.runtime;

import io.camunda.connector.runtime.inbound.importer.ProcessDefinitionImporter;
import jakarta.annotation.Nullable;
import java.util.Map;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health.Builder;

public class OperateHealthIndicator extends AbstractHealthIndicator {

  private final ProcessDefinitionImporter processDefinitionImporter;

  public OperateHealthIndicator(@Nullable ProcessDefinitionImporter processDefinitionImporter) {
    this.processDefinitionImporter = processDefinitionImporter;
  }

  @Override
  protected void doHealthCheck(Builder builder) {
    var details = Map.of("operateEnabled", processDefinitionImporter != null);
    if (processDefinitionImporter == null || processDefinitionImporter.isReady()) {
      builder.up().withDetails(details);
    } else {
      builder.down().withDetails(details);
    }
  }
}
