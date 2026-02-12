/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.caller;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.textract.model.TextractRequestData;
import java.util.HashSet;
import java.util.Set;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

public interface TextractCaller<T> {

  String WRONG_ANALYZE_TYPE_MSG = "At least one analyze type should be selected";

  T call(final TextractRequestData request, final TextractClient textractClient) throws Exception;

  default S3Object prepareS3Obj(final TextractRequestData requestData) {
    return S3Object.builder()
        .bucket(requestData.documentS3Bucket())
        .name(requestData.documentName())
        .version(requestData.documentVersion())
        .build();
  }

  default Set<FeatureType> prepareFeatureTypes(final TextractRequestData request) {
    final Set<FeatureType> types = new HashSet<>();
    if (request.analyzeForms()) {
      types.add(FeatureType.FORMS);
    }
    if (request.analyzeLayout()) {
      types.add(FeatureType.LAYOUT);
    }
    if (request.analyzeSignatures()) {
      types.add(FeatureType.SIGNATURES);
    }
    if (request.analyzeTables()) {
      types.add(FeatureType.TABLES);
    }
    if (request.analyzeQueries()) {
      types.add(FeatureType.QUERIES);
    }
    if (types.isEmpty()) {
      throw new IllegalArgumentException(WRONG_ANALYZE_TYPE_MSG);
    }
    return types;
  }

  default QueriesConfig prepareQueryConfig(final TextractRequestData requestData) {
    if (requestData.query() != null) {
      return QueriesConfig.builder()
          .queries(Query.builder().text(requestData.query()).build())
          .build();
    } else if (requestData.analyzeQueries()) {
      throw new ConnectorInputException(
          "The 'query' field must be provided when 'analyzeQueries' is set to true.");
    }
    return null;
  }

  default DocumentLocation prepareDocumentLocation(final TextractRequestData request) {
    final S3Object s3Obj = prepareS3Obj(request);
    return DocumentLocation.builder().s3Object(s3Obj).build();
  }
}
