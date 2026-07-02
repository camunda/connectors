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
import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.optimizer.core.Pass;
import io.camunda.connector.optimizer.core.PropertyUtils;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Merges properties that are identical except for their condition's equals/oneOf value.
 *
 * <p>Groups properties by (binding, value, presentation fields). If all members of a group
 * condition on the same discriminator and differ only in their condition's equals/oneOf value,
 * replaces them with a single property whose condition is oneOf:[union of values].
 *
 * <p>The merged property's id is chosen by stripping per-operation prefixes from the source ids to
 * a neutral form.
 *
 * <p>Properties with {@code AllMatch} or {@code IsActive} conditions are passed through unchanged —
 * merging through composite conditions would require a real combinator pass.
 */
public class MergeByIdentityPass implements Pass {

  public static final String ID = "merge-by-identity";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String description() {
    return "Merge properties that differ only in their condition value";
  }

  @Override
  public ElementTemplate apply(ElementTemplate template) {
    List<Property> properties = template.properties();
    if (properties.isEmpty()) {
      return template;
    }

    Map<PropertyIdentity, List<Property>> groups = new LinkedHashMap<>();
    for (Property prop : properties) {
      groups.computeIfAbsent(new PropertyIdentity(prop), k -> new ArrayList<>()).add(prop);
    }

    Set<String> reservedIds = new HashSet<>();
    for (Map.Entry<PropertyIdentity, List<Property>> entry : groups.entrySet()) {
      if (entry.getValue().size() == 1) {
        String id = entry.getValue().get(0).getId();
        if (id != null) {
          reservedIds.add(id);
        }
      }
    }

    List<Property> optimized = new ArrayList<>();
    for (Map.Entry<PropertyIdentity, List<Property>> entry : groups.entrySet()) {
      List<Property> group = entry.getValue();
      if (group.size() == 1) {
        optimized.add(group.get(0));
        continue;
      }
      Property merged = tryMerge(group, reservedIds);
      if (merged != null) {
        optimized.add(merged);
        if (merged.getId() != null) {
          reservedIds.add(merged.getId());
        }
      } else {
        optimized.addAll(group);
      }
    }

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
        optimized,
        template.icon(),
        template.steps(),
        template.presets());
  }

  /**
   * Tries to merge a group of properties into one. Returns null if they can't be merged.
   *
   * <p>Properties can be merged if:
   *
   * <ul>
   *   <li>All have conditions
   *   <li>All condition on the same discriminator property
   *   <li>All use equals or oneOf (not allMatch or other complex conditions)
   * </ul>
   */
  private Property tryMerge(List<Property> group, Set<String> reservedIds) {
    // Check all have conditions
    List<PropertyCondition> conditions = new ArrayList<>();
    for (Property prop : group) {
      if (prop.getCondition() == null) {
        return null; // Can't merge unconditional properties
      }
      conditions.add(prop.getCondition());
    }

    // Check all condition on the same discriminator and collect values
    String discriminator = null;
    Set<String> allValues = new LinkedHashSet<>();

    for (PropertyCondition cond : conditions) {
      switch (cond) {
        case PropertyCondition.Equals eq -> {
          if (discriminator == null) {
            discriminator = eq.property();
          } else if (!discriminator.equals(eq.property())) {
            return null; // Different discriminators
          }
          // Discriminator values are strings in well-formed templates (dropdown choices are
          // String-typed). Refuse to coerce non-strings via String.valueOf because that conflates
          // null with the literal text "null" and Boolean.TRUE with "true".
          Object value = eq.equals();
          if (!(value instanceof String s)) {
            return null;
          }
          allValues.add(s);
        }
        case PropertyCondition.OneOf oneOf -> {
          if (discriminator == null) {
            discriminator = oneOf.property();
          } else if (!discriminator.equals(oneOf.property())) {
            return null; // Different discriminators
          }
          allValues.addAll(oneOf.oneOf());
        }
        default -> {
          return null; // Unsupported condition type (AllMatch, IsActive, etc.)
        }
      }
    }

    if (discriminator == null) {
      return null;
    }

    String newId = chooseNeutralId(group, reservedIds);

    // Build the merged condition
    PropertyCondition mergedCondition;
    if (allValues.size() == 1) {
      mergedCondition = new PropertyCondition.Equals(discriminator, allValues.iterator().next());
    } else {
      mergedCondition = new PropertyCondition.OneOf(discriminator, new ArrayList<>(allValues));
    }

    // Return the merged property with new id and condition
    Property base = group.get(0);
    return PropertyUtils.withCondition(PropertyUtils.withId(base, newId), mergedCondition);
  }

  /**
   * Choose a neutral id by stripping per-operation prefixes.
   *
   * <p>E.g., {@code search_query_locale}, {@code autocomplete_query_locale} -&gt; {@code
   * query_locale}.
   *
   * <p>If the chosen id collides with an existing one, {@link #disambiguate} appends a numeric
   * suffix until a free id is found.
   */
  private String chooseNeutralId(List<Property> group, Set<String> reservedIds) {
    List<String> ids =
        group.stream().map(Property::getId).filter(Objects::nonNull).collect(Collectors.toList());

    if (ids.isEmpty()) {
      return disambiguate("merged_property", reservedIds);
    }

    String commonSuffix = longestCommonSuffix(ids);
    String candidate;
    if (!commonSuffix.isEmpty() && commonSuffix.contains("_")) {
      candidate = commonSuffix.replaceFirst("^_+", "");
    } else {
      // Prefer the longest original id, with lexicographic tie-break so the choice is
      // deterministic across JVMs and run-to-run.
      candidate =
          ids.stream()
              .max(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()))
              .orElse("merged_property");
    }
    return disambiguate(candidate, reservedIds);
  }

  private String disambiguate(String candidate, Set<String> reservedIds) {
    if (!reservedIds.contains(candidate)) {
      return candidate;
    }
    int suffix = 2;
    while (reservedIds.contains(candidate + "_" + suffix)) {
      suffix++;
    }
    return candidate + "_" + suffix;
  }

  /** Find the longest common suffix among a list of strings. */
  private String longestCommonSuffix(List<String> strings) {
    if (strings.isEmpty()) {
      return "";
    }
    if (strings.size() == 1) {
      return strings.get(0);
    }

    String first = strings.get(0);
    int suffixLen = 0;
    for (int i = 1; i <= first.length(); i++) {
      String suffix = first.substring(first.length() - i);
      boolean allMatch = strings.stream().allMatch(s -> s.endsWith(suffix));
      if (allMatch) {
        suffixLen = i;
      } else {
        break;
      }
    }
    return first.substring(first.length() - suffixLen);
  }

  /**
   * Identity of a property: everything except the condition and id.
   *
   * <p>Two properties have the same identity if they produce the same output (binding + value) and
   * have the same presentation fields (label, description, etc).
   */
  private static class PropertyIdentity {
    private final Property property;

    PropertyIdentity(Property prop) {
      this.property = prop;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof PropertyIdentity)) return false;
      PropertyIdentity that = (PropertyIdentity) o;

      // Compare all fields except id and condition
      Property p1 = this.property;
      Property p2 = that.property;

      return Objects.equals(p1.getBinding(), p2.getBinding())
          && Objects.equals(p1.getValue(), p2.getValue())
          && Objects.equals(p1.getGeneratedValue(), p2.getGeneratedValue())
          && Objects.equals(p1.getType(), p2.getType())
          && Objects.equals(p1.getGroup(), p2.getGroup())
          && Objects.equals(p1.getLabel(), p2.getLabel())
          && Objects.equals(p1.getDescription(), p2.getDescription())
          && Objects.equals(p1.isOptional(), p2.isOptional())
          && Objects.equals(p1.getFeel(), p2.getFeel())
          && Objects.equals(p1.getConstraints(), p2.getConstraints())
          && Objects.equals(p1.getPlaceholder(), p2.getPlaceholder())
          && Objects.equals(p1.getTooltip(), p2.getTooltip())
          && Objects.equals(p1.getExampleValue(), p2.getExampleValue())
          && Objects.equals(choicesOf(p1), choicesOf(p2));
    }

    private static List<DropdownProperty.DropdownChoice> choicesOf(Property p) {
      return p instanceof DropdownProperty d ? d.getChoices() : null;
    }

    @Override
    public int hashCode() {
      Property p = this.property;
      return Objects.hash(
          p.getBinding(),
          p.getValue(),
          p.getGeneratedValue(),
          p.getType(),
          p.getGroup(),
          p.getLabel(),
          p.getDescription(),
          p.isOptional(),
          p.getFeel(),
          p.getConstraints(),
          p.getPlaceholder(),
          p.getTooltip(),
          p.getExampleValue(),
          choicesOf(p));
    }
  }
}
