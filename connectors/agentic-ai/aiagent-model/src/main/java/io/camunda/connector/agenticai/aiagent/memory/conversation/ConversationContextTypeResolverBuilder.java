/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Type resolver for {@link ConversationContext} which resolves the {@code type} property against
 * all subtypes contributed through {@link ConversationContextSubtypes} service providers, in
 * addition to subtypes registered on the {@code ObjectMapper} itself.
 */
public class ConversationContextTypeResolverBuilder extends StdTypeResolverBuilder {

  private static final class Discovered {
    private static final List<NamedType> SUBTYPES =
        toNamedTypes(
            ServiceLoader.load(
                ConversationContextSubtypes.class,
                ConversationContextSubtypes.class.getClassLoader()));
  }

  @Override
  protected TypeIdResolver idResolver(
      MapperConfig<?> config,
      JavaType baseType,
      PolymorphicTypeValidator subtypeValidator,
      Collection<NamedType> subtypes,
      boolean forSer,
      boolean forDeser) {
    var allSubtypes = new ArrayList<NamedType>();
    if (subtypes != null) {
      allSubtypes.addAll(subtypes);
    }
    allSubtypes.addAll(Discovered.SUBTYPES);
    return super.idResolver(config, baseType, subtypeValidator, allSubtypes, forSer, forDeser);
  }

  static List<NamedType> toNamedTypes(Iterable<ConversationContextSubtypes> providers) {
    var namedTypes = new ArrayList<NamedType>();
    for (var provider : providers) {
      for (var subtype : provider.subtypes()) {
        var typeName = subtype.getAnnotation(JsonTypeName.class);
        if (typeName == null) {
          throw new IllegalStateException(
              "ConversationContext subtype %s listed by %s must be annotated with @JsonTypeName"
                  .formatted(subtype.getName(), provider.getClass().getName()));
        }
        namedTypes.add(new NamedType(subtype, typeName.value()));
      }
    }
    return List.copyOf(namedTypes);
  }
}
