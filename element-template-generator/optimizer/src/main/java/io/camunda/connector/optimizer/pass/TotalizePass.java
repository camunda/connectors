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

import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.optimizer.core.Pass;
import io.camunda.connector.optimizer.core.PropertyUtils;
import java.util.*;

/**
 * Removes conditions that cover all possible values of a discriminator.
 *
 * <p>When a property's oneOf condition covers every choice of its discriminator, the condition is
 * redundant and can be dropped, making the property unconditional.
 */
public class TotalizePass implements Pass {

  public static final String ID = "totalize";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String description() {
    return "Remove conditions that cover all possible discriminator values";
  }

  @Override
  public ElementTemplate apply(ElementTemplate template) {
    List<Property> properties = template.properties();
    if (properties.isEmpty()) {
      return template;
    }

    // Build a map of discriminator property id -> set of all choice values
    Map<String, Set<String>> discriminatorChoices = buildDiscriminatorChoices(properties);

    // Transform properties, removing total conditions
    List<Property> optimized = new ArrayList<>();
    for (Property prop : properties) {
      PropertyCondition condition = prop.getCondition();
      if (condition == null) {
        optimized.add(prop);
        continue;
      }

      // Check if this is a totalized condition
      String discriminator = null;
      Set<String> coveredValues = new HashSet<>();

      switch (condition) {
        case PropertyCondition.Equals eq -> {
          discriminator = eq.property();
          coveredValues.add(String.valueOf(eq.equals()));
        }
        case PropertyCondition.OneOf oneOf -> {
          discriminator = oneOf.property();
          coveredValues.addAll(oneOf.oneOf());
        }
        default -> {
          // Keep properties with other condition types as-is
          optimized.add(prop);
          continue;
        }
      }

      Set<String> allChoices = discriminatorChoices.get(discriminator);
      if (allChoices != null && coveredValues.equals(allChoices)) {
        // Condition covers all choices - remove it
        optimized.add(PropertyUtils.withoutCondition(prop));
      } else {
        optimized.add(prop);
      }
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
        optimized,
        template.icon());
  }

  /**
   * Build a map of discriminator property id to all its choice values.
   *
   * <p>Discriminators are dropdown properties that other properties condition on.
   */
  private Map<String, Set<String>> buildDiscriminatorChoices(List<Property> properties) {
    Map<String, Set<String>> result = new HashMap<>();

    for (Property prop : properties) {
      if (prop instanceof DropdownProperty dropdown) {
        List<DropdownChoice> choices = dropdown.getChoices();
        if (choices != null && !choices.isEmpty()) {
          Set<String> values = new HashSet<>();
          for (DropdownChoice choice : choices) {
            values.add(choice.value());
          }
          result.put(prop.getId(), values);
        }
      }
    }

    return result;
  }
}
