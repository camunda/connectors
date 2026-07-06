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
package io.camunda.connector.generator.java.util;

import static io.camunda.connector.util.reflection.ReflectionUtil.getAllFields;

import io.camunda.connector.generator.dsl.GroupStep;
import io.camunda.connector.generator.dsl.LeafStep;
import io.camunda.connector.generator.dsl.Preset;
import io.camunda.connector.generator.dsl.Step;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Second-pass walker that produces operation-metadata {@code steps} and {@code presets} for
 * sealed-type Java connectors (input-class style — see {@link TemplatePropertiesUtil}).
 */
public final class StepTreeWalker {

  private StepTreeWalker() {}

  public static StepTreeResult walk(Class<?> inputType) {
    if (inputType == null) {
      return StepTreeResult.empty();
    }
    OperationRoot root = findOperationRoot(inputType);
    if (root == null) {
      return StepTreeResult.empty();
    }

    String outerDiscriminatorName = requireDiscriminatorName(root.type());
    String outerKey = prefixed(root.propertyPathPrefix(), outerDiscriminatorName);
    List<Step> steps = new ArrayList<>();
    List<Preset> presets = new ArrayList<>();

    for (Class<?> sub : nonIgnoredSubtypes(root.type())) {
      String subId = requireSubTypeId(sub);
      String presetIdPrefix = outerDiscriminatorName + "_" + subId;
      Map<String, String> assignment = new LinkedHashMap<>();
      assignment.put(outerKey, subId);
      Step child = buildStep(sub, presetIdPrefix, assignment, root.propertyPathPrefix(), presets);
      if (child != null) {
        steps.add(child);
      }
    }
    return new StepTreeResult(steps, presets);
  }

  private static OperationRoot findOperationRoot(Class<?> inputType) {
    Deque<Entry> queue = new ArrayDeque<>();
    Set<Class<?>> visited = new HashSet<>();
    queue.add(new Entry(inputType, ""));
    while (!queue.isEmpty()) {
      Entry e = queue.poll();
      if (!visited.add(e.type)) {
        continue;
      }
      if (e.type.isSealed() && hasAnyLeafKeywords(e.type)) {
        return new OperationRoot(e.type, e.prefix);
      }
      if (e.type.isSealed()) {
        // Pass through unmatched sealed types via their permits chain
        for (Class<?> sub : nonIgnoredSubtypes(e.type)) {
          if (!visited.contains(sub) && !isLeafType(sub)) {
            queue.add(new Entry(sub, e.prefix));
          }
        }
      }
      for (Field f : getAllFields(e.type)) {
        NestedProperties np = f.getAnnotation(NestedProperties.class);
        boolean addPath = np == null || np.addNestedPath();
        String nextPrefix =
            addPath ? (e.prefix.isEmpty() ? f.getName() : e.prefix + "." + f.getName()) : e.prefix;
        for (Class<?> ft : extractCandidateTypes(f)) {
          if (visited.contains(ft) || isLeafType(ft)) {
            continue;
          }
          queue.add(new Entry(ft, nextPrefix));
        }
      }
    }
    return null;
  }

  private record Entry(Class<?> type, String prefix) {}

  private record OperationRoot(Class<?> type, String propertyPathPrefix) {}

  private static String prefixed(String pathPrefix, String name) {
    return pathPrefix.isEmpty() ? name : pathPrefix + "." + name;
  }

  /** Candidate types reachable through a field. */
  private static List<Class<?>> extractCandidateTypes(Field f) {
    Class<?> raw = f.getType();
    if (raw.isArray()) {
      Class<?> component = raw.getComponentType();
      return component == null ? List.of() : List.of(component);
    }
    Type generic = f.getGenericType();
    if (generic instanceof ParameterizedType pt) {
      List<Class<?>> out = new ArrayList<>();
      for (Type arg : pt.getActualTypeArguments()) {
        Class<?> c = rawClassOf(arg);
        if (c != null) {
          out.add(c);
        }
      }
      if (!out.isEmpty()) {
        return out;
      }
    }
    return List.of(raw);
  }

  private static Class<?> rawClassOf(Type t) {
    if (t instanceof Class<?> c) {
      return c;
    }
    if (t instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c) {
      return c;
    }
    return null;
  }

  private static boolean isLeafType(Class<?> t) {
    return t.isPrimitive()
        || t.isEnum()
        || t.isArray()
        || t == String.class
        || t == Object.class
        || Number.class.isAssignableFrom(t)
        || Boolean.class == t
        || Character.class == t;
  }

  private static Step buildStep(
      Class<?> node,
      String presetIdPrefix,
      Map<String, String> assignment,
      String propertyPathPrefix,
      List<Preset> presets) {
    TemplateSubType st = node.getAnnotation(TemplateSubType.class);
    String name =
        (st != null && !st.label().isBlank())
            ? st.label()
            : TemplatePropertiesUtil.transformIdIntoLabel(node.getSimpleName());
    String description = (st != null && !st.description().isBlank()) ? st.description() : null;

    if (node.isSealed()) { // group branch: recurse via the permits chain (path prefix unchanged)
      return buildSealedGroup(
          node, presetIdPrefix, assignment, propertyPathPrefix, presets, name, description);
    }

    // Non-sealed: a record may either be a true leaf (has keywords) or an intermediate group
    // that exposes a nested sealed @TemplateDiscriminatorProperty via a record component
    // (e.g., Email's `Smtp.smtpAction → SmtpAction`). In the latter case, recurse via that
    // field and update the property path prefix per @NestedProperties(addNestedPath).
    NestedDiscriminatorField nested = findSingleNestedDiscriminatorField(node, propertyPathPrefix);
    boolean hasKeywords = st != null && st.keywords().length > 0;
    // Only treat the nested discriminator as an operation group when it actually contains
    // operations (i.e. has keywords reachable from its subtypes). A nested discriminator that
    // is purely a configuration picker (e.g. DocumentSplitter inside EmbedDocumentOperation)
    // has no keywords and should not pull the enclosing record into group-node territory.
    boolean nestedHasOperations = nested != null && hasAnyLeafKeywords(nested.sealedType());
    if (nestedHasOperations) {
      if (hasKeywords) {
        throw new IllegalStateException(
            "Type "
                + node.getCanonicalName()
                + " declares @TemplateSubType(keywords = {...}) and also exposes a nested "
                + "@TemplateDiscriminatorProperty via field \""
                + nested.fieldName()
                + "\" (type "
                + nested.sealedType().getCanonicalName()
                + "). A node must be either a leaf (with keywords) or an intermediate group that "
                + "groups further operations — not both.");
      }
      return buildSealedGroup(
          nested.sealedType(),
          presetIdPrefix,
          assignment,
          nested.childPathPrefix(),
          presets,
          name,
          description);
    }

    // leaf branch
    if (!hasKeywords) {
      throw new IllegalStateException(
          "Leaf "
              + node.getCanonicalName()
              + " is missing required @TemplateSubType(keywords = {...}). Every leaf in a connector "
              + "that participates in operation metadata must declare at least one keyword.");
    }
    presets.add(new Preset(presetIdPrefix, assignment));
    return new LeafStep(name, description, Arrays.asList(st.keywords()), presetIdPrefix);
  }

  /**
   * Build a group step from a sealed type by walking its {@code permits} chain. Used by both
   * sealed-typed nodes (entered directly via permits) and non-sealed nodes that expose a nested
   * sealed discriminator via a record component.
   */
  private static Step buildSealedGroup(
      Class<?> sealedNode,
      String presetIdPrefix,
      Map<String, String> assignment,
      String propertyPathPrefix,
      List<Preset> presets,
      String name,
      String description) {
    String innerDiscriminator = requireDiscriminatorName(sealedNode);
    String innerKey = prefixed(propertyPathPrefix, innerDiscriminator);
    if (assignment.containsKey(innerKey)) {
      throw new IllegalStateException(
          "Sealed type "
              + sealedNode.getCanonicalName()
              + " declares @TemplateDiscriminatorProperty(name = \""
              + innerDiscriminator
              + "\"), which collides with an outer discriminator at the same property path \""
              + innerKey
              + "\". Inner sealed groups must declare a distinct discriminator name.");
    }
    List<Step> children = new ArrayList<>();
    for (Class<?> child : nonIgnoredSubtypes(sealedNode)) {
      String childId = requireSubTypeId(child);
      String childPresetId = presetIdPrefix + "_" + innerDiscriminator + "_" + childId;
      Map<String, String> childAssignment = new LinkedHashMap<>(assignment);
      childAssignment.put(innerKey, childId);
      Step built = buildStep(child, childPresetId, childAssignment, propertyPathPrefix, presets);
      if (built != null) {
        children.add(built);
      }
    }
    if (children.isEmpty()) {
      // Every permitted subtype was @TemplateSubType(ignore = true) — skip this branch entirely
      return null;
    }
    return new GroupStep(name, description, children);
  }

  /**
   * Find the single record component on {@code node} whose type is a sealed {@link
   * TemplateDiscriminatorProperty}-annotated hierarchy. Returns {@code null} if there is none (so
   * the caller can treat the node as a leaf). Throws if multiple such fields exist — an
   * intermediate group node must have exactly one nested discriminator to be unambiguous.
   */
  private static NestedDiscriminatorField findSingleNestedDiscriminatorField(
      Class<?> node, String currentPropertyPathPrefix) {
    List<NestedDiscriminatorField> found = new ArrayList<>();
    for (Field f : getAllFields(node)) {
      NestedProperties np = f.getAnnotation(NestedProperties.class);
      boolean addPath = np == null || np.addNestedPath();
      String childPrefix =
          addPath
              ? (currentPropertyPathPrefix.isEmpty()
                  ? f.getName()
                  : currentPropertyPathPrefix + "." + f.getName())
              : currentPropertyPathPrefix;
      for (Class<?> ft : extractCandidateTypes(f)) {
        if (ft.isSealed() && ft.isAnnotationPresent(TemplateDiscriminatorProperty.class)) {
          found.add(new NestedDiscriminatorField(f.getName(), ft, childPrefix));
        }
      }
    }
    if (found.isEmpty()) {
      return null;
    }
    if (found.size() > 1) {
      StringBuilder fields = new StringBuilder();
      for (int i = 0; i < found.size(); i++) {
        if (i > 0) {
          fields.append(", ");
        }
        fields
            .append(found.get(i).fieldName())
            .append(" -> ")
            .append(found.get(i).sealedType().getSimpleName());
      }
      throw new IllegalStateException(
          "Type "
              + node.getCanonicalName()
              + " exposes more than one nested @TemplateDiscriminatorProperty field ("
              + fields
              + "). An intermediate operation node must have exactly one nested discriminator-bearing"
              + " field so the operation hierarchy is unambiguous.");
    }
    return found.get(0);
  }

  private record NestedDiscriminatorField(
      String fieldName, Class<?> sealedType, String childPathPrefix) {}

  private static boolean hasAnyLeafKeywords(Class<?> root) {
    Deque<Class<?>> queue = new ArrayDeque<>();
    Set<Class<?>> seen = new HashSet<>();
    queue.add(root);
    while (!queue.isEmpty()) {
      Class<?> c = queue.pop();
      if (!seen.add(c)) {
        continue;
      }
      if (c.isSealed()) {
        queue.addAll(nonIgnoredSubtypes(c));
      } else {
        TemplateSubType st = c.getAnnotation(TemplateSubType.class);
        if (st != null && st.keywords().length > 0) {
          return true;
        }
        // Recurse through @NestedProperties-annotated fields only, to discover nested sealed
        // discriminator hierarchies (e.g. Protocol → Smtp → SmtpAction → SmtpSendEmail).
        // Bare field references (e.g. NestedAuthCarryingOps.action) are NOT followed — that
        // field is a reachable operation root on its own, not a sub-discriminator of NestedAuth.
        for (Field f : getAllFields(c)) {
          if (!f.isAnnotationPresent(NestedProperties.class)) continue;
          for (Class<?> ft : extractCandidateTypes(f)) {
            if (ft.isSealed() && !seen.contains(ft)) {
              queue.add(ft);
            }
          }
        }
      }
    }
    return false;
  }

  private static String requireDiscriminatorName(Class<?> type) {
    TemplateDiscriminatorProperty d = type.getAnnotation(TemplateDiscriminatorProperty.class);
    if (d == null || d.name().isBlank()) {
      throw new IllegalStateException(
          "Sealed type "
              + type.getCanonicalName()
              + " participates in operation metadata but does not declare "
              + "@TemplateDiscriminatorProperty(name = ...).");
    }
    // Use id() when explicitly set — that is the generated template property's id.
    // When id is not set (empty default) the ETG also uses name() as the property id.
    return d.id().isBlank() ? d.name() : d.id();
  }

  private static String requireSubTypeId(Class<?> sub) {
    TemplateSubType subType = sub.getAnnotation(TemplateSubType.class);
    if (subType == null || subType.id().isBlank()) {
      throw new IllegalStateException(
          "Type "
              + sub.getCanonicalName()
              + " participates in operation metadata but does not declare "
              + "@TemplateSubType(id = \"...\", keywords = {\"...\"}).");
    }
    return subType.id();
  }

  private static List<Class<?>> nonIgnoredSubtypes(Class<?> sealed) {
    return Arrays.stream(sealed.getPermittedSubclasses())
        .filter(
            s -> {
              TemplateSubType st = s.getAnnotation(TemplateSubType.class);
              return st == null || !st.ignore();
            })
        .toList();
  }
}
