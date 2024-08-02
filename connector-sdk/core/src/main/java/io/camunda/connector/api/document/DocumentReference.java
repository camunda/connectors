package io.camunda.connector.api.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = DocumentReference.DISCRIMINATOR_KEY)
public sealed interface DocumentReference {

  String DISCRIMINATOR_KEY = "$documentType";

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      property = DocumentReference.DISCRIMINATOR_KEY)
  sealed interface StaticDocumentReference extends DocumentReference {

    @JsonTypeName("camunda")
    record CamundaDocumentReference(
        @JsonProperty("$storeId")
        String storeId,
        @JsonProperty("$documentId")
        String documentId,
        @JsonProperty("$metadata")
        Map<String, Object> metadata
    ) implements StaticDocumentReference {}

    @JsonTypeName("external")
    record ExternalDocumentReference(
        @JsonProperty("$url")
        String url
    ) implements StaticDocumentReference {}
  }

  @JsonTypeName("operation")
  record DocumentOperationReference(
      @JsonProperty("$reference")
      StaticDocumentReference reference,
      @JsonProperty("$operation")
      DocumentOperation operation
  ) {}
}

{
 "documentType": "operation",
  "$reference": {
    "documentType": "camunda",
    "$storeId": "myStore",
    "$documentId": "myDocument",
    "$metadata": {
      "key": "value"
    }
  },
"$operation": {
    "$name": "read",
    "$params": {
      "key": "value"
    }
  }
}
