package io.camunda.connector.runtime.core.inbound;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.api.inbound.InboundConnectorDefinition;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Group of inbound connector elements that share the same deduplication ID. */
public record InboundConnectorDefinitionImpl(
    String type,
    String tenantId,
    String deduplicationId,
    @JsonIgnore Map<String, String> rawPropertiesWithoutKeywords,
    List<InboundConnectorElementImpl> elements
) implements InboundConnectorDefinition {
  public InboundConnectorDefinitionImpl(List<InboundConnectorElementImpl> definitions) {
    this(
        extractType(definitions),
        extractTenantId(definitions),
        extractDeduplicationId(definitions),
        extractRawProperties(definitions),
        definitions
    );
  }

  private static String extractType(List<InboundConnectorElementImpl> definitions) {
    if (definitions.stream()
        .map(InboundConnectorElementImpl::type)
        .distinct()
        .count()
        > 1) {
      throw new IllegalArgumentException(
          "All elements in a group must have the same type");
    }
    return definitions.getFirst().type();
  }

  private static String extractTenantId(List<InboundConnectorElementImpl> definitions) {
    if (definitions.stream()
        .map(InboundConnectorElementImpl::tenantId)
        .distinct()
        .count()
        > 1) {
      throw new IllegalArgumentException(
          "All elements in a group must have the same tenant ID");
    }
    return definitions.getFirst().tenantId();
  }

  private static String extractDeduplicationId(List<InboundConnectorElementImpl> definitions) {
    if (definitions.stream()
        .map(InboundConnectorElementImpl::deduplicationId)
        .distinct()
        .count()
        > 1) {
      throw new IllegalArgumentException(
          "All elements in a group must have the same deduplication ID");
    }
    return definitions.getFirst().deduplicationId();
  }

  private static Map<String, String> extractRawProperties(List<InboundConnectorElementImpl> definitions) {
    if (definitions.stream()
        .map(InboundConnectorElementImpl::rawPropertiesWithoutKeywords)
        .distinct()
        .count()
        > 1) {

      throw new IllegalArgumentException(
          "All elements in a group must have the same properties (excluding runtime-level properties)");
    }
    return definitions.getFirst().rawProperties();
  }
}
