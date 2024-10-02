/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.caller;

import static io.camunda.connector.comprehend.caller.ComprehendCaller.READ_ACTION_WITHOUT_FEATURES_EX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.amazonaws.services.comprehend.model.DocumentReadFeatureTypes;
import com.amazonaws.services.comprehend.model.DocumentReaderConfig;
import com.amazonaws.services.comprehend.model.StartDocumentClassificationJobResult;
import io.camunda.connector.comprehend.model.ComprehendDocumentReadAction;
import io.camunda.connector.comprehend.model.ComprehendDocumentReadMode;
import io.camunda.connector.comprehend.model.ComprehendSyncRequestData;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ComprehendCallerTest {

  private final ComprehendCaller<StartDocumentClassificationJobResult, ComprehendSyncRequestData>
      caller = (a1, a2) -> null;

  @Test
  void prepareDocumentReaderConfigWithEmptyReadActionReturnEmpty() {
    ComprehendSyncRequestData request = prepareSyncData(true, true);
    Optional<DocumentReaderConfig> documentReader = caller.prepareDocumentReaderConfig(request);
    assertThat(documentReader).isEmpty();
  }

  @Test
  void
      prepareDocumentReaderConfigWithTextractAnalyzeDocActionWithoutSelectedFeaturesShouldThrowEx() {
    var request =
        new ComprehendSyncRequestData(
            "text",
            ComprehendDocumentReadAction.TEXTRACT_ANALYZE_DOCUMENT,
            ComprehendDocumentReadMode.NO_DATA,
            false,
            false,
            "arn");

    Exception ex =
        assertThrows(
            IllegalArgumentException.class, () -> caller.prepareDocumentReaderConfig(request));
    assertThat(ex.getMessage()).isEqualTo(READ_ACTION_WITHOUT_FEATURES_EX);
  }

  @Test
  void
      prepareDocumentReaderConfigWithReadActionNonEqualTextractAnalyzeDocActionShouldNotAddFeatures() {
    var request =
        new ComprehendSyncRequestData(
            "text",
            ComprehendDocumentReadAction.TEXTRACT_DETECT_DOCUMENT_TEXT,
            ComprehendDocumentReadMode.NO_DATA,
            true,
            true,
            "arn");

    Optional<DocumentReaderConfig> readerConfig = caller.prepareDocumentReaderConfig(request);
    assertThat(readerConfig)
        .contains(
            new DocumentReaderConfig()
                .withDocumentReadAction(request.getDocumentReadAction().name()));
  }

  @Test
  void prepareDocumentReaderConfigWithALLSelectedFields() {
    var request =
        new ComprehendSyncRequestData(
            "text",
            ComprehendDocumentReadAction.TEXTRACT_ANALYZE_DOCUMENT,
            ComprehendDocumentReadMode.SERVICE_DEFAULT,
            true,
            true,
            "arn");
    Optional<DocumentReaderConfig> readerConfig = caller.prepareDocumentReaderConfig(request);
    assertThat(readerConfig)
        .contains(
            new DocumentReaderConfig()
                .withDocumentReadAction(request.getDocumentReadAction().name())
                .withDocumentReadMode(request.getDocumentReadMode().name())
                .withFeatureTypes(
                    List.of(
                        DocumentReadFeatureTypes.FORMS.name(),
                        DocumentReadFeatureTypes.TABLES.name())));
  }

  @Test
  void prepareFeaturesWithAllUnselectedFeatures() {
    ComprehendSyncRequestData request = prepareSyncData(false, false);
    List<String> features = caller.prepareFeatures(request);

    assertThat(features.size()).isZero();
  }

  @Test
  void prepareFeaturesWithAllSelectedFeatures() {
    ComprehendSyncRequestData request = prepareSyncData(true, true);
    List<String> features = caller.prepareFeatures(request);

    assertThat(features.size()).isEqualTo(2);
  }

  @Test
  void prepareFeaturesWithOneSelectedFeature() {
    ComprehendSyncRequestData request = prepareSyncData(true, false);
    List<String> features = caller.prepareFeatures(request);

    assertThat(features.size()).isOne();
  }

  private ComprehendSyncRequestData prepareSyncData(boolean tables, boolean forms) {
    return new ComprehendSyncRequestData(
        "text",
        ComprehendDocumentReadAction.NO_DATA,
        ComprehendDocumentReadMode.NO_DATA,
        tables,
        forms,
        "arn::");
  }
}
