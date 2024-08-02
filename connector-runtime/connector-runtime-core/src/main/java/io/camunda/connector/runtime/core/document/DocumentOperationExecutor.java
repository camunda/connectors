package io.camunda.connector.runtime.core.document;

import io.camunda.connector.api.document.DocumentReference.DocumentOperationReference;

public interface DocumentOperationExecutor {

  String getName();

  Object execute(DocumentOperationReference operationReference);
}
