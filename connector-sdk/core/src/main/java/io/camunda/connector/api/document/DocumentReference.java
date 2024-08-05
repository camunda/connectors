package io.camunda.connector.api.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import java.util.Optional;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = DocumentReference.DISCRIMINATOR_KEY)
public sealed interface DocumentReference {

  String DISCRIMINATOR_KEY = "$documentType";

  /**
   * Document references may have operations associated with them. Operation indicates that the
   * document should not be used as is, but should be transformed or processed in some way. This
   * processing must take place in the context of the connector.
   */
  Optional<DocumentOperation> operation();

  @JsonTypeName("camunda")
  record CamundaDocumentReference(
      String storeId,
      String documentId,
      Map<String, Object> metadata,
      Optional<DocumentOperation> operation
  ) implements DocumentReference {}

  @JsonTypeName("external")
  record ExternalDocumentReference(
      String url,
      Optional<DocumentOperation> operation
  ) implements DocumentReference {}
}
