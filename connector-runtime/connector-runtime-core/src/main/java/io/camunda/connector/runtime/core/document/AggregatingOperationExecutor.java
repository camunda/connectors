package io.camunda.connector.runtime.core.document;

import io.camunda.connector.api.document.DocumentReference.DocumentOperationReference;
import java.util.Map;

public class AggregatingOperationExecutor implements DocumentOperationExecutor {

  private final Map<String, DocumentOperationExecutor> executors;

  public AggregatingOperationExecutor(Map<String, DocumentOperationExecutor> executors) {
    this.executors = executors;
  }

  @Override
  public String getName() {
    return "";
  }

  @Override
  public Object execute(DocumentOperationReference operationReference) {
    DocumentOperationExecutor executor = executors.get(operationReference.operation().name());
    if (executor == null) {
      throw new IllegalArgumentException("No executor found for operation " + operationReference.operation().name());
    }
    return executor.execute(operationReference);
  }
}
