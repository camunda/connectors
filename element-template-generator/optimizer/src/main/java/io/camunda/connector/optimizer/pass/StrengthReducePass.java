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
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.optimizer.core.Pass;
import io.camunda.connector.optimizer.core.PropertyUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Simplifies singleton oneOf conditions to equals.
 *
 * <p>Transforms {@code oneOf: ["x"]} into {@code equals: "x"}. This is a cosmetic improvement that
 * matches what generators emit naturally.
 *
 * <p>Only rewrites top-level conditions; nested conditions inside {@code AllMatch} are not
 * traversed.
 */
public class StrengthReducePass implements Pass {

  public static final String ID = "strength-reduce";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String description() {
    return "Simplify singleton oneOf conditions to equals";
  }

  @Override
  public ElementTemplate apply(ElementTemplate template) {
    List<Property> properties = template.properties();
    if (properties.isEmpty()) {
      return template;
    }

    List<Property> optimized = new ArrayList<>();
    for (Property prop : properties) {
      PropertyCondition condition = prop.getCondition();
      if (condition instanceof PropertyCondition.OneOf oneOf && oneOf.oneOf().size() == 1) {
        // Convert oneOf:[x] to equals:x
        String singleValue = oneOf.oneOf().get(0);
        PropertyCondition newCondition =
            new PropertyCondition.Equals(oneOf.property(), singleValue);
        optimized.add(PropertyUtils.withCondition(prop, newCondition));
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
}
