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

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.connector.api.inbound.ElementTemplateDetails;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.ProcessElementWithRuntimeData;
import java.util.Optional;

public record Result(String type, String id, String version, String key) {

  public static Result getResult(ActivatedJob job) {
    String type = job.getType();
    String id = job.getCustomHeaders().getOrDefault("elementTemplateId", "unknown");
    String version = job.getCustomHeaders().getOrDefault("elementTemplateVersion", "unknown");
    String key = type + "_" + id + "_" + version;
    return new Result(type, id, version, key);
  }

  public static Result getResult(InboundConnectorElement connectorElement) {
    String type = connectorElement.type();
    String id =
        Optional.of(connectorElement)
            .map(InboundConnectorElement::element)
            .map(ProcessElementWithRuntimeData::elementTemplateDetails)
            .map(ElementTemplateDetails::id)
            .orElse("unknown");
    String version =
        Optional.of(connectorElement)
            .map(InboundConnectorElement::element)
            .map(ProcessElementWithRuntimeData::elementTemplateDetails)
            .map(ElementTemplateDetails::version)
            .orElse("unknown");
    String key = type + "_" + id + "_" + version;
    return new Result(type, id, version, key);
  }

  public String createKey(String actionActivated) {
    return this.key() + "_" + actionActivated;
  }
}
