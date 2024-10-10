/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.caller;

import static io.camunda.connector.comprehend.model.ComprehendDocumentReadAction.TEXTRACT_ANALYZE_DOCUMENT;

import com.amazonaws.AmazonWebServiceResult;
import com.amazonaws.ResponseMetadata;
import com.amazonaws.services.comprehend.AmazonComprehendClient;
import com.amazonaws.services.comprehend.model.DocumentReadFeatureTypes;
import com.amazonaws.services.comprehend.model.DocumentReaderConfig;
import io.camunda.connector.comprehend.model.ComprehendDocumentReadAction;
import io.camunda.connector.comprehend.model.ComprehendDocumentReadMode;
import io.camunda.connector.comprehend.model.ComprehendRequestData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ComprehendCaller<
    T extends AmazonWebServiceResult<ResponseMetadata>, R extends ComprehendRequestData> {

  String READ_ACTION_WITHOUT_FEATURES_EX =
      "If you chose TEXTRACT_ANALYZE_DOCUMENT as the read action, "
          + "you must specify one feature types";

  Logger LOGGER = LoggerFactory.getLogger(ComprehendCaller.class);

  int INITIAL_FEATURES_CAPACITY = 2;

  T call(AmazonComprehendClient client, R requestData);

  default Optional<DocumentReaderConfig> prepareDocumentReaderConfig(
      ComprehendRequestData requestData) {
    if (requestData.getDocumentReadAction() == (ComprehendDocumentReadAction.NO_DATA)) {
      return Optional.empty();
    }

    var documentReaderConfig =
        new DocumentReaderConfig()
            .withDocumentReadAction(requestData.getDocumentReadAction().name());

    if (requestData.getDocumentReadMode() != (ComprehendDocumentReadMode.NO_DATA)) {
      documentReaderConfig.withDocumentReadMode(requestData.getDocumentReadMode().name());
    }

    if (requestData.getDocumentReadAction() == TEXTRACT_ANALYZE_DOCUMENT) {
      List<String> features = prepareFeatures(requestData);
      if (features.isEmpty()) {
        LOGGER.warn("DocumentReadAction: TEXTRACT_ANALYZE_DOCUMENT, but features not selected.");
        throw new IllegalArgumentException(READ_ACTION_WITHOUT_FEATURES_EX);
      }
      documentReaderConfig.withFeatureTypes(features);
    }

    return Optional.of(documentReaderConfig);
  }

  default List<String> prepareFeatures(ComprehendRequestData requestData) {
    List<String> features = new ArrayList<>(INITIAL_FEATURES_CAPACITY);
    if (requestData.getFeatureTypeForms()) {
      features.add(DocumentReadFeatureTypes.FORMS.name());
    }
    if (requestData.getFeatureTypeTables()) {
      features.add(DocumentReadFeatureTypes.TABLES.name());
    }
    return features;
  }
}
