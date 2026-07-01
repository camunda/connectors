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
package io.camunda.connector.optimizer.pass;

import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.HiddenProperty;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.optimizer.core.Pass;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reorders properties in a stable canonical order.
 *
 * <p>Sorts properties by:
 *
 * <ol>
 *   <li>Group (empty group first, then alphabetically)
 *   <li>Visibility (Hidden type first, then visible types)
 *   <li>ID (alphabetically)
 * </ol>
 *
 * <p>This makes regeneration diffs reviewable and ensures a consistent property order.
 */
public class ReorderPass implements Pass {

  public static final String ID = "reorder";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String description() {
    return "Reorder properties in canonical order (group, visibility, id)";
  }

  @Override
  public ElementTemplate apply(ElementTemplate template) {
    List<Property> properties = template.properties();
    if (properties.isEmpty()) {
      return template;
    }

    List<Property> sorted = new ArrayList<>(properties);
    sorted.sort(
        Comparator.comparing((Property p) -> p.getGroup() == null ? "" : p.getGroup())
            .thenComparing(p -> p instanceof HiddenProperty ? 0 : 1)
            .thenComparing(p -> p.getId() == null ? "" : p.getId()));

    if (sorted.equals(properties)) {
      return template;
    }

    return new ElementTemplate(
        template.id(),
        template.name(),
        template.version(),
        template.documentationRef(),
        template.engines(),
        template.description(),
        template.keywords(),
        template.appliesTo(),
        template.elementType(),
        template.groups(),
        sorted,
        template.icon());
  }
}
