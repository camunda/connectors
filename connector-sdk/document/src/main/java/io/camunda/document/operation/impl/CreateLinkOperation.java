package io.camunda.document.operation.impl;

import io.camunda.document.Document;
import io.camunda.document.operation.IntrinsicOperation;
import io.camunda.document.operation.IntrinsicOperationParams;
import io.camunda.document.operation.IntrinsicOperationResult;

public class CreateLinkOperation implements IntrinsicOperation<String> {

  @Override
  public IntrinsicOperationResult<String> execute(IntrinsicOperationParams params) {
    switch (params) {
      case IntrinsicOperationParams.Positional args -> {

        var document = (Document) args.params().get(0);
        return document.generateLink();

        // check that we only have 1 arg & it's a document
        args.para
      }
    }
  }
}
