package io.camunda.connector.runtime.core.document.operation;

import io.camunda.connector.api.document.DocumentReference.DocumentOperationReference;
import io.camunda.connector.runtime.core.document.DocumentOperationExecutor;

public class Base64Operation implements DocumentOperationExecutor {

  @Override
  public String getName() {
    return "";
  }

  @Override
  public Object execute(DocumentOperationReference operationReference) {
    return null;
  }
}
