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
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.generator.dsl.PropertyGroup;
import io.camunda.connector.optimizer.core.Pass;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Emits properties in a stable canonical order, guaranteeing that every discriminator property
 * appears before the properties that condition on it.
 *
 * <p>The secondary ordering within the same topological wave is: group (null-group first, then by
 * the order declared in {@code template.groups()}) → visible properties before {@link
 * HiddenProperty} → lexicographic id.
 *
 * <p>If the dependency graph contains a cycle (which valid element templates should never have),
 * the pass returns the template unchanged rather than producing a truncated list.
 */
public class ReorderPass implements Pass {

  public static final String ID = "reorder";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String description() {
    return "Emit properties in stable canonical order, discriminators before dependents";
  }

  @Override
  public ElementTemplate apply(ElementTemplate template) {
    List<Property> properties = template.properties();
    if (properties.size() <= 1) {
      return template;
    }

    // Properties without an ID can't participate in the dependency graph; collect them separately.
    List<Property> nullIdProps = new ArrayList<>();
    Map<String, Property> byId = new LinkedHashMap<>();
    for (Property p : properties) {
      if (p.getId() == null) {
        nullIdProps.add(p);
      } else {
        byId.put(p.getId(), p);
      }
    }

    if (byId.isEmpty()) {
      return template;
    }

    // Build adjacency for Kahn's algorithm.
    // successors[D] = properties that condition on D (D must be emitted before them).
    Map<String, Set<String>> successors = new LinkedHashMap<>();
    Map<String, Integer> inDegree = new LinkedHashMap<>();
    for (String id : byId.keySet()) {
      successors.put(id, new LinkedHashSet<>());
      inDegree.put(id, 0);
    }
    for (Property p : byId.values()) {
      for (String disc : discriminatorsOf(p.getCondition())) {
        if (byId.containsKey(disc) && !disc.equals(p.getId())) {
          if (successors.get(disc).add(p.getId())) {
            inDegree.merge(p.getId(), 1, Integer::sum);
          }
        }
      }
    }

    // Kahn's topological sort with canonical tie-breaking.
    Comparator<String> canonical = canonicalOrder(byId, template.groups());
    PriorityQueue<String> ready = new PriorityQueue<>(canonical);
    for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
      if (e.getValue() == 0) {
        ready.add(e.getKey());
      }
    }

    List<Property> sorted = new ArrayList<>(byId.size());
    while (!ready.isEmpty()) {
      String cur = ready.poll();
      sorted.add(byId.get(cur));
      for (String succ : successors.get(cur)) {
        if (inDegree.merge(succ, -1, Integer::sum) == 0) {
          ready.add(succ);
        }
      }
    }

    // Cycle guard: a valid element template has no cycles, but don't truncate on unexpected input.
    if (sorted.size() != byId.size()) {
      return template;
    }

    sorted.addAll(nullIdProps);

    return new ElementTemplate(
        template.id(),
        template.name(),
        template.version(),
        template.category(),
        template.documentationRef(),
        template.engines(),
        template.description(),
        template.keywords(),
        template.appliesTo(),
        template.elementType(),
        template.groups(),
        sorted,
        template.icon(),
        template.steps(),
        template.presets());
  }

  /** Returns all discriminator property IDs referenced (transitively) by {@code cond}. */
  private static Set<String> discriminatorsOf(PropertyCondition cond) {
    if (cond == null) {
      return Set.of();
    }
    return switch (cond) {
      case PropertyCondition.Equals eq -> Set.of(eq.property());
      case PropertyCondition.OneOf oneOf -> Set.of(oneOf.property());
      case PropertyCondition.IsActive isActive -> Set.of(isActive.property());
      case PropertyCondition.AllMatch allMatch -> {
        Set<String> result = new LinkedHashSet<>();
        for (PropertyCondition sub : allMatch.allMatch()) {
          result.addAll(discriminatorsOf(sub));
        }
        yield result;
      }
    };
  }

  /**
   * Canonical comparator for property IDs within the same topological wave.
   *
   * <p>Order: null-group first, then by position in {@code groups}, then {@link HiddenProperty}
   * last, then lexicographic id.
   */
  private static Comparator<String> canonicalOrder(
      Map<String, Property> byId, List<PropertyGroup> groups) {
    Map<String, Integer> groupRank = new HashMap<>();
    for (int i = 0; i < groups.size(); i++) {
      groupRank.put(groups.get(i).id(), i + 1); // null-group is rank 0
    }
    return Comparator.comparingInt(
            (String id) -> {
              String group = byId.get(id).getGroup();
              return group == null ? 0 : groupRank.getOrDefault(group, Integer.MAX_VALUE);
            })
        .thenComparingInt(id -> byId.get(id) instanceof HiddenProperty ? 1 : 0)
        .thenComparing(Comparator.naturalOrder());
  }
}
