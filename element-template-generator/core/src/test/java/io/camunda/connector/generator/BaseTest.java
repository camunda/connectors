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
package io.camunda.connector.generator;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.Property;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class BaseTest {

  protected Property getPropertyById(String id, ElementTemplate template) {
    return template.properties().stream()
        .filter(p -> id.equals(p.getId()))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Property with id " + id + " not found"));
  }

  protected Property getPropertyByLabel(String label, ElementTemplate template) {
    return template.properties().stream()
        .filter(p -> label.equals(p.getLabel()))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Property with label " + label + " not found"));
  }

  protected void checkPropertyGroups(
      List<Map.Entry<String, String>> groupNamesAndLabels,
      ElementTemplate template,
      boolean orderMatters) {
    List<Map.Entry<String, String>> groups =
        template.groups().stream()
            .map(group -> Map.entry(group.id(), group.label()))
            .collect(Collectors.toList());

    if (orderMatters) {
      assertThat(groups).containsExactlyElementsOf(groupNamesAndLabels);
    } else {
      assertThat(groups).containsExactlyInAnyOrderElementsOf(groupNamesAndLabels);
    }
  }
}
