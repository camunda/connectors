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
package io.camunda.connector.runtime.inbound.executable;

import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record ActiveExecutableResponse(
    UUID executableId,
    Class<? extends InboundConnectorExecutable> executableClass,
    List<InboundConnectorElement> elements,
    Health health,
    Collection<Activity> logs,
    Long activationTimestamp) {

  private static final Logger LOG = LoggerFactory.getLogger(ActiveExecutableResponse.class);

  public Map<String, String> data() {
    Map<String, String> data = new HashMap<>(elements().getFirst().connectorLevelProperties());
    var executableClass = executableClass();

    if (executableClass != null
        && WebhookConnectorExecutable.class.isAssignableFrom(executableClass)) {
      try {
        var properties = elements().getFirst().connectorLevelProperties();
        var contextPath = properties.get("inbound.context");
        data.put("path", contextPath);
      } catch (Exception e) {
        LOG.error("ERROR: webhook connector doesn't have context path property", e);
      }
    }
    return data;
  }
}
