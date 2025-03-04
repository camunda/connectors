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

import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectorDataMapper {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectorDataMapper.class);

  public Map<String, String> webhookMapper(ActiveExecutableResponse response) {
    Map<String, String> data = Map.of();
    var executableClass = response.executableClass();

    if (executableClass != null
        && WebhookConnectorExecutable.class.isAssignableFrom(executableClass)) {
      try {
        var properties = response.elements().getFirst().connectorLevelProperties();
        var contextPath = properties.get("inbound.context");
        data = Map.of("path", contextPath);
      } catch (Exception e) {
        LOG.error("ERROR: webhook connector doesn't have context path property", e);
      }
    }
    return data;
  }

  public Map<String, String> allPropertiesMapper(ActiveExecutableResponse response) {
    return response.elements().getFirst().connectorLevelProperties();
  }
}
