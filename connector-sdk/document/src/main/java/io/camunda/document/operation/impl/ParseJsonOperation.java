package io.camunda.document.operation.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.document.operation.IntrinsicOperation;
import io.camunda.document.operation.IntrinsicOperationParameter;
import io.camunda.document.operation.IntrinsicOperationResult;
import io.camunda.document.operation.IntrinsicOperationResult.Failure.ValidationFailure;
import java.util.List;

public class ParseJsonOperation implements IntrinsicOperation<Object> {

  private final ObjectMapper objectMapper;

  public ParseJsonOperation(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public IntrinsicOperationResult<Object> execute(List<? extends IntrinsicOperationParameter> arguments) {
    if (arguments.size() != 1) {
      return new ValidationFailure<>(
          "ParseJson operation expects a single JSON string as argument");
    }
    final var maybeJsonString = arguments.get(0);

    if (maybeJsonString.isDocumentParameter()) {
      var doc = maybeJsonString.asDocumentParameter().asByteArray();
      var jsonString = new String(doc);
    }

    if (arg) {
      return new ValidationFailure<>(
          "ParseJson operation expects a single JSON string as argument");
    }


    final var jsonString = (String) maybeJsonString.asValueParameter();

    try {
      final var json = objectMapper.readValue(jsonString, Object.class);
      return new IntrinsicOperationResult.Success<>(json);
    } catch (Exception e) {
      return new IntrinsicOperationResult.Failure.ExecutionFailure<>("Failed to parse JSON", e);
    }
  }
}
