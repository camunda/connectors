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

import io.camunda.connector.textract.model.DocumentLocationType;
import io.camunda.connector.textract.model.TextractExecutionType;
import io.camunda.connector.textract.model.TextractRequestData;
import java.util.Set;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.textract.model.AnalyzeDocumentResponse;
import software.amazon.awssdk.services.textract.model.DocumentLocation;
import software.amazon.awssdk.services.textract.model.S3Object;

class TextractCallerTest {

  private final TextractCaller<AnalyzeDocumentResponse> textractCaller = (data, client) -> null;

  @Test
  void prepareS3Obj() {
    S3Object s3Object = textractCaller.prepareS3Obj(FULL_FILLED_ASYNC_TEXTRACT_DATA);

    assertThat(s3Object.bucket()).isEqualTo(FULL_FILLED_ASYNC_TEXTRACT_DATA.documentS3Bucket());
    assertThat(s3Object.name()).isEqualTo(FULL_FILLED_ASYNC_TEXTRACT_DATA.documentName());
    assertThat(s3Object.version()).isEqualTo(FULL_FILLED_ASYNC_TEXTRACT_DATA.documentVersion());
  }

  @Test
  void prepareFeatureTypesAllEnabled() {
    TextractRequestData requestData1 =
        new TextractRequestData(
            DocumentLocationType.S3,
            "test-bucket",
            "test-object",
            "1",
            null,
            TextractExecutionType.SYNC,
            true,
            true,
            true,
            true,
            false,
            "",
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
            DocumentLocationType.S3,
            "test-bucket",
            "test-object",
            "1",
            null,
            TextractExecutionType.SYNC,
            false,
            false,
            false,
            false,
            false,
            "",
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
            DocumentLocationType.S3,
            "test-bucket",
            "test-object",
            "1",
            null,
            TextractExecutionType.SYNC,
            true,
            false,
            false,
            true,
            false,
            "",
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

    assertThat(documentLocation.s3Object()).isNotNull();
  }
}
