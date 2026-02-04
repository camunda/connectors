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
package io.camunda.connector.runtime.designtime.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.designtime.Option;
import io.camunda.connector.runtime.core.designtime.DefaultValueProviderContext;
import io.camunda.connector.runtime.core.designtime.ValueProviderConnectorFactory;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ValueProviderRestController {

  private final ObjectMapper mapper;
  private final ValueProviderConnectorFactory factory;

  public ValueProviderRestController(ObjectMapper mapper) {
    this.mapper = mapper;
    this.factory = new ValueProviderConnectorFactory();
  }

  @PostMapping("/connectors/{type}/options/{fieldName}")
  List<Option> getOptions(
      @PathVariable(value = "type") String type,
      @PathVariable(value = "fieldName") String fieldName,
      @RequestBody ValueProviderRequest parameters) {
    return factory
        .findBy(type, fieldName)
        .map(
            valueProvider -> {
              var context = new DefaultValueProviderContext(mapper, parameters.data());
              try {
                return valueProvider.getOptions(context);
              } catch (Exception e) {
                throw new RuntimeException("Failed to get options from ValueProvider", e);
              }
            })
        .orElse(List.of());
  }
}
