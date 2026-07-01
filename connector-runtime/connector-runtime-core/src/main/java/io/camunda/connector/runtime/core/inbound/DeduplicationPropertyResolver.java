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
package io.camunda.connector.runtime.core.inbound;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Derives the deduplication property <em>prefixes</em> contributed by one or more connector data
 * classes, using Jackson type introspection only (no element-template-generator dependency).
 *
 * <p>Inbound connector properties are bound from a flat map of dotted keys (for example {@code
 * inbound.auth.type}) that {@link InboundPropertyHandler#readWrappedProperties} unflattens before
 * Jackson binds them. A class's deduplication-relevant keys are therefore exactly its Jackson
 * serialization paths.
 *
 * <p>Rather than enumerating every leaf path — which would require resolving polymorphic subtypes —
 * this resolver walks only "plain" bean types and emits a <em>prefix</em> at every stopping point:
 * a scalar, a container (array/collection/map), or a polymorphic type ({@code @JsonTypeInfo} /
 * interface / abstract). A raw property key belongs to the deduplication scope iff it equals one of
 * these prefixes or is nested under it (see {@link #matchesScope(String, List)}). For example, the
 * single prefix {@code inbound.auth} covers {@code inbound.auth.type}, {@code
 * inbound.auth.jwt.jwkUrl} and any other subtype field, without the resolver ever enumerating the
 * sealed subtypes.
 *
 * <p>Limitation: this relies on the bound key matching the Jackson property name, which holds for
 * all connectors (the runtime binds raw properties via Jackson). Properties bound under a name that
 * differs from their Jackson path are not supported.
 */
public final class DeduplicationPropertyResolver {

  // Property names are independent of (de)serialization modules, so a vanilla mapper suffices and
  // avoids coupling the runtime to the connectors' object-mapper configuration.
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private DeduplicationPropertyResolver() {}

  /**
   * Computes the union of deduplication prefixes contributed by the given data classes. {@link
   * Void#TYPE Void} entries are ignored. The result is empty when no classes are supplied, which
   * callers interpret as "deduplicate on all properties".
   */
  public static List<String> resolvePrefixes(List<Class<?>> classes) {
    Set<String> prefixes = new LinkedHashSet<>();
    for (Class<?> clazz : classes) {
      if (clazz != null && clazz != Void.class) {
        collect("", MAPPER.constructType(clazz), prefixes, new HashSet<>());
      }
    }
    return new ArrayList<>(prefixes);
  }

  /**
   * Returns {@code true} if the given raw property key falls within the deduplication scope, i.e.
   * it equals one of the prefixes or is nested under it ({@code prefix + "."}).
   */
  public static boolean matchesScope(String key, List<String> prefixes) {
    for (String prefix : prefixes) {
      if (key.equals(prefix) || key.startsWith(prefix + ".")) {
        return true;
      }
    }
    return false;
  }

  private static void collect(
      String prefix, JavaType type, Set<String> out, Set<JavaType> visited) {
    if (!visited.add(type)) {
      // recursion cycle: treat the current path as a stopping point
      addPrefix(prefix, out);
      return;
    }
    try {
      if (!isRecursableBean(type)) {
        addPrefix(prefix, out);
        return;
      }
      BeanDescription description = MAPPER.getSerializationConfig().introspect(type);
      List<BeanPropertyDefinition> properties = description.findProperties();
      if (properties.isEmpty()) {
        addPrefix(prefix, out);
        return;
      }
      for (BeanPropertyDefinition property : properties) {
        String childPrefix =
            prefix.isEmpty() ? property.getName() : prefix + "." + property.getName();
        collect(childPrefix, property.getPrimaryType(), out, visited);
      }
    } finally {
      visited.remove(type);
    }
  }

  private static void addPrefix(String prefix, Set<String> out) {
    if (!prefix.isEmpty()) {
      out.add(prefix);
    }
  }

  /**
   * A type is recursed into only when it is a concrete, application-level bean. Scalars, enums,
   * containers, JDK types and polymorphic bases are stopping points whose single prefix already
   * covers every nested key.
   */
  private static boolean isRecursableBean(JavaType type) {
    if (type.isContainerType()
        || type.isCollectionLikeType()
        || type.isMapLikeType()
        || type.isArrayType()) {
      return false;
    }
    Class<?> raw = type.getRawClass();
    if (raw.isPrimitive()
        || raw.isEnum()
        || raw.isInterface()
        || Modifier.isAbstract(raw.getModifiers())) {
      return false;
    }
    String name = raw.getName();
    if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jakarta.")) {
      return false;
    }
    // A polymorphic base is a stopping point: its prefix transparently covers the discriminator and
    // every subtype's fields, so we never need to resolve the subtypes.
    return !raw.isAnnotationPresent(JsonTypeInfo.class);
  }
}
