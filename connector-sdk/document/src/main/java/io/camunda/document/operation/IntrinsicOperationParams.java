package io.camunda.document.operation;

import java.util.List;

public sealed interface IntrinsicOperationParams {

  record Positional(List<Object> params) implements IntrinsicOperationParams {}

  // TODO: named parameters
}
