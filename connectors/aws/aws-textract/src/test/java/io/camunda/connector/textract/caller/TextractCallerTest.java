/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.caller;

import static io.camunda.connector.textract.caller.TextractCaller.WRONG_ANALYZE_TYPE_MSG;
import static io.camunda.connector.textract.util.TextractTestUtils.FULL_FILLED_ASYNC_TEXTRACT_DATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.amazonaws.services.textract.model.AnalyzeDocumentResult;
import com.amazonaws.services.textract.model.DocumentLocation;
import com.amazonaws.services.textract.model.S3Object;
import io.camunda.connector.textract.model.TextractExecutionType;
import io.camunda.connector.textract.model.TextractRequestData;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TextractCallerTest {

  private final TextractCaller<AnalyzeDocumentResult> textractCaller = (data, client) -> null;

  @Test
  void prepareS3Obj() {
    S3Object s3Object = textractCaller.prepareS3Obj(FULL_FILLED_ASYNC_TEXTRACT_DATA);

    assertThat(s3Object.getBucket()).isEqualTo(FULL_FILLED_ASYNC_TEXTRACT_DATA.documentS3Bucket());
    assertThat(s3Object.getName()).isEqualTo(FULL_FILLED_ASYNC_TEXTRACT_DATA.documentName());
    assertThat(s3Object.getVersion()).isEqualTo(FULL_FILLED_ASYNC_TEXTRACT_DATA.documentVersion());
  }

  @Test
  void prepareFeatureTypesAllEnabled() {
    TextractRequestData requestData1 =
        new TextractRequestData(
            TextractExecutionType.SYNC,
            "test-bucket",
            "test-object",
            "1",
            true,
            true,
            true,
            true,
            "token",
            "client-request-token",
            "job-tag",
            "notification-channel",
            "role-arn",
            "outputBucket",
            "prefix");
    Set<String> featureTypes = textractCaller.prepareFeatureTypes(requestData1);
    assertThat(featureTypes).containsExactlyInAnyOrder("FORMS", "LAYOUT", "SIGNATURES", "TABLES");
  }

  @Test
  void prepareFeatureTypesNoFeaturesEnabled() {
    TextractRequestData requestData =
        new TextractRequestData(
            TextractExecutionType.SYNC,
            "test-bucket",
            "test-object",
            "1",
            false,
            false,
            false,
            false,
            "token",
            "client-request-token",
            "job-tag",
            "notification-channel",
            "role-arn",
            "outputBucket",
            "prefix");

    Exception exception =
        assertThrows(
            IllegalArgumentException.class, () -> textractCaller.prepareFeatureTypes(requestData));

    assertThat(exception.getMessage()).isEqualTo(WRONG_ANALYZE_TYPE_MSG);
  }

  @Test
  void prepareFeatureTypesOnlyTablesAndLayout() {
    TextractRequestData requestData =
        new TextractRequestData(
            TextractExecutionType.SYNC,
            "test-bucket",
            "test-object",
            "1",
            true,
            false,
            false,
            true,
            "token",
            "client-request-token",
            "job-tag",
            "notification-channel",
            "role-arn",
            "outputBucket",
            "prefix");
    Set<String> featureTypes = textractCaller.prepareFeatureTypes(requestData);
    assertThat(featureTypes).containsExactlyInAnyOrder("TABLES", "LAYOUT");
  }

  @Test
  void prepareDocumentLocation() {
    DocumentLocation documentLocation =
        textractCaller.prepareDocumentLocation(FULL_FILLED_ASYNC_TEXTRACT_DATA);

    assertThat(documentLocation.getS3Object()).isNotNull();
  }
}
