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
import software.amazon.awssdk.services.textract.model.DocumentLocation;
import software.amazon.awssdk.services.textract.model.FeatureType;
import software.amazon.awssdk.services.textract.model.QueriesConfig;
import software.amazon.awssdk.services.textract.model.Query;
import software.amazon.awssdk.services.textract.model.S3Object;

/**
 * {@code T} is the connector-owned result type this caller returns; {@code C} is the AWS SDK v2
 * client type it calls. Unlike AWS SDK v1 (where {@code AmazonTextractAsync extends
 * AmazonTextract}, so both sync and polling/async callers could share a single client type), v2's
 * {@code TextractClient} and {@code TextractAsyncClient} are unrelated interfaces - hence the
 * second type parameter.
 */
public interface TextractCaller<T, C> {

  String WRONG_ANALYZE_TYPE_MSG = "At least one analyze type should be selected";

  T call(final TextractRequestData request, final C textractClient) throws Exception;

  default S3Object prepareS3Obj(final TextractRequestData requestData) {
    return S3Object.builder()
        .bucket(requestData.documentS3Bucket())
        .name(requestData.documentName())
        .version(requestData.documentVersion())
        .build();
  }

  default Set<String> prepareFeatureTypes(final TextractRequestData request) {
    final Set<String> types = new HashSet<>();
    if (request.analyzeForms()) {
      types.add(FeatureType.FORMS.name());
    }
    if (request.analyzeLayout()) {
      types.add(FeatureType.LAYOUT.name());
    }
    if (request.analyzeSignatures()) {
      types.add(FeatureType.SIGNATURES.name());
    }
    if (request.analyzeTables()) {
      types.add(FeatureType.TABLES.name());
    }
    if (request.analyzeQueries()) {
      types.add(FeatureType.QUERIES.name());
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
