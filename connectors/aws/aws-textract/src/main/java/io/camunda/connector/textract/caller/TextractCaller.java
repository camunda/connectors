/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.caller;

import com.amazonaws.AmazonWebServiceResult;
import com.amazonaws.ResponseMetadata;
import com.amazonaws.services.textract.AmazonTextract;
import com.amazonaws.services.textract.model.DocumentLocation;
import com.amazonaws.services.textract.model.FeatureType;
import com.amazonaws.services.textract.model.S3Object;
import io.camunda.connector.textract.model.TextractRequestData;
import java.util.HashSet;
import java.util.Set;

public interface TextractCaller<T extends AmazonWebServiceResult<ResponseMetadata>> {

  String WRONG_ANALYZE_TYPE_MSG = "At least one analyze type should be selected";

  T call(final TextractRequestData request, final AmazonTextract textractClient) throws Exception;

  default S3Object prepareS3Obj(final TextractRequestData requestData) {
    return new S3Object()
        .withBucket(requestData.documentS3Bucket())
        .withName(requestData.documentName())
        .withVersion(requestData.documentVersion());
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
    if (types.isEmpty()) {
      throw new IllegalArgumentException(WRONG_ANALYZE_TYPE_MSG);
    }
    return types;
  }

  default DocumentLocation prepareDocumentLocation(final TextractRequestData request) {
    final S3Object s3Obj = prepareS3Obj(request);
    return new DocumentLocation().withS3Object(s3Obj);
  }
}
