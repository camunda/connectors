/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.conversation;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Type resolver for {@link ConversationContext} which resolves the {@code type} property against
 * all subtypes contributed through {@link ConversationContextSubtypes} service providers, in
 * addition to subtypes registered on the {@code ObjectMapper} itself.
 *
 * <p>Provider discovery runs once. A provider that cannot be loaded, or a subtype that cannot be
 * registered, is logged at ERROR level and skipped; all other subtypes stay available. An unknown
 * type id fails with Jackson's {@link com.fasterxml.jackson.databind.exc.InvalidTypeIdException}
 * listing the known type ids.
 */
public class ConversationContextTypeResolverBuilder extends StdTypeResolverBuilder {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ConversationContextTypeResolverBuilder.class);

  private static final String SERVICES_RESOURCE =
      "META-INF/services/" + ConversationContextSubtypes.class.getName();

  /**
   * Upper bound for provider loading errors, protecting against an iterator that never advances.
   */
  private static final int MAX_PROVIDER_ERRORS = 100;

  private static final class Discovered {
    private static final List<NamedType> SUBTYPES =
        discoverSubtypes(ConversationContextSubtypes.class.getClassLoader());
  }

  @Override
  protected TypeIdResolver idResolver(
      MapperConfig<?> config,
      JavaType baseType,
      PolymorphicTypeValidator subtypeValidator,
      @Nullable Collection<NamedType> subtypes,
      boolean forSer,
      boolean forDeser) {
    var allSubtypes = new ArrayList<NamedType>();
    if (subtypes != null) {
      allSubtypes.addAll(subtypes);
    }
    allSubtypes.addAll(Discovered.SUBTYPES);
    return super.idResolver(config, baseType, subtypeValidator, allSubtypes, forSer, forDeser);
  }

  static List<NamedType> discoverSubtypes(@Nullable ClassLoader classLoader) {
    Iterator<ConversationContextSubtypes> providers;
    try {
      providers = ServiceLoader.load(ConversationContextSubtypes.class, classLoader).iterator();
    } catch (RuntimeException | ServiceConfigurationError | LinkageError e) {
      LOGGER.error(
          "Failed to discover {} service providers, no custom conversation context subtypes are"
              + " available. Check {}.",
          ConversationContextSubtypes.class.getSimpleName(),
          SERVICES_RESOURCE,
          e);
      return List.of();
    }
    return discoverSubtypes(providers);
  }

  static List<NamedType> discoverSubtypes(Iterator<ConversationContextSubtypes> providers) {
    var namedTypes = new ArrayList<NamedType>();
    var classesByTypeId = new HashMap<String, Class<?>>();

    int errors = 0;
    while (errors < MAX_PROVIDER_ERRORS) {
      ConversationContextSubtypes provider;
      try {
        if (!providers.hasNext()) {
          break;
        }
        provider = providers.next();
      } catch (ServiceConfigurationError | RuntimeException | LinkageError e) {
        errors++;
        LOGGER.error(
            "Skipping a {} service provider which could not be loaded: {}. Fix the entry in {}"
                + " (the listed provider class must exist, be public and have a public no-argument"
                + " constructor).",
            ConversationContextSubtypes.class.getSimpleName(),
            e.getMessage(),
            SERVICES_RESOURCE,
            e);
        continue;
      }

      try {
        registerProviderSubtypes(provider, namedTypes, classesByTypeId);
      } catch (RuntimeException | LinkageError e) {
        LOGGER.error(
            "Skipping remaining subtypes of {} provider {}: {}",
            ConversationContextSubtypes.class.getSimpleName(),
            provider.getClass().getName(),
            e.toString(),
            e);
      }
    }

    if (errors >= MAX_PROVIDER_ERRORS) {
      LOGGER.error(
          "Stopped discovering {} service providers after {} loading errors",
          ConversationContextSubtypes.class.getSimpleName(),
          errors);
    }

    return List.copyOf(namedTypes);
  }

  private static void registerProviderSubtypes(
      ConversationContextSubtypes provider,
      List<NamedType> namedTypes,
      Map<String, Class<?>> classesByTypeId) {
    var providerName = provider.getClass().getName();

    List<Class<? extends ConversationContext>> subtypes;
    try {
      subtypes = provider.subtypes();
    } catch (RuntimeException | LinkageError e) {
      LOGGER.error(
          "Skipping {} provider {} because subtypes() failed: {}",
          ConversationContextSubtypes.class.getSimpleName(),
          providerName,
          e.toString(),
          e);
      return;
    }

    // providers are external code: null values are possible despite the declared types
    if (subtypes == null) {
      LOGGER.error(
          "Skipping {} provider {}: subtypes() returned null. Return the list of subtype classes.",
          ConversationContextSubtypes.class.getSimpleName(),
          providerName);
      return;
    }

    for (var subtype : subtypes) {
      if (subtype == null) {
        LOGGER.error(
            "Skipping null ConversationContext subtype listed by {}. Remove it from"
                + " {}.subtypes().",
            providerName,
            providerName);
        continue;
      }

      var typeName = subtype.getAnnotation(JsonTypeName.class);
      if (typeName == null) {
        LOGGER.error(
            "Skipping ConversationContext subtype {} listed by {}: it is not annotated with"
                + " @JsonTypeName. Annotate it with @JsonTypeName(\"<type id>\") or remove it from"
                + " {}.subtypes().",
            subtype.getName(),
            providerName,
            providerName);
        continue;
      }

      var typeId = typeName.value();
      var existing = classesByTypeId.get(typeId);
      if (existing == subtype) {
        continue;
      }
      if (existing != null) {
        LOGGER.error(
            "Skipping ConversationContext subtype {} listed by {}: type id '{}' is already used by"
                + " {}. Use a unique @JsonTypeName value.",
            subtype.getName(),
            providerName,
            typeId,
            existing.getName());
        continue;
      }

      classesByTypeId.put(typeId, subtype);
      namedTypes.add(new NamedType(subtype, typeId));
    }
  }
}
