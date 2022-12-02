/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sendgrid;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;

import com.google.gson.Gson;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public class BaseTest {

  protected static final Gson gson = new Gson();

  private static final String FAIL_REQUEST_CASES_PATH =
      "src/test/resources/requests/validation/request-without-one-field-fail-test-cases.json";
  private static final String FAIL_REQUEST_WITH_WRONG_SENDER_EMAIL =
      "src/test/resources/requests/validation/validate-sender-email-tests-cases.json";
  private static final String FAIL_REQUEST_WITH_WRONG_SENDER_NAME =
      "src/test/resources/requests/validation/validate-sender-name-tests-cases.json";
  private static final String FAIL_REQUEST_WITH_WRONG_RECEIVER_EMAIL =
      "src/test/resources/requests/validation/validate-receiver-email-tests-cases.json";
  private static final String FAIL_REQUEST_WITH_WRONG_RECEIVER_NAME =
      "src/test/resources/requests/validation/validate-receiver-name-tests-cases.json";

  private static final String SUCCESS_REPLACE_SECRETS_REQUEST_CASES_PATH =
      "src/test/resources/requests/replace-secrets-success-test-cases.json";
  private static final String SUCCESS_REPLACE_SECRETS_CONTENT_REQUEST_CASES_PATH =
      "src/test/resources/requests/replace-secrets-content-success-test-cases.json";
  private static final String SUCCESS_REPLACE_SECRETS_TEMPLATE_REQUEST_CASES_PATH =
      "src/test/resources/requests/replace-secrets-template-success-test-cases.json";
  private static final String SUCCESS_SEND_MAIL_BY_TEMPLATE_TEMPLATE_REQUEST_CASES_PATH =
      "src/test/resources/requests/send-mail-by-template-success-cases.json";
  private static final String SUCCESS_SEND_MAIL_WITH_CONTENT_REQUEST_CASES_PATH =
      "src/test/resources/requests/send-mail-with-content-success-cases.json";

  protected interface ActualValue {
    String API_KEY = "send_grid_key";
    String RECEIVER_EMAIL = "jane.doe@example.com";
    String RECEIVER_NAME = "Jane Doe";
    String SENDER_EMAIL = "john.doe@example.com";
    String SENDER_NAME = "John Doe";

    interface Content {
      String SUBJECT = "subject_test";
      String TYPE = "text/json";
      String VALUE = "Hello world";
    }

    interface Template {
      String ID = "d-0b51e8f77bf8450fae379e0639ca0d11";

      interface Data {
        String ACCOUNT_NAME = "Feuerwehrmann Sam";
        String SHIP_ADDRESS = "Krossener Str. 24";
        String SHIP_ZIP = "10245";

        String KEY_ACCOUNT_NAME = "accountName";
        String KEY_SHIP_ADDRESS = "shipAddress";
        String KEY_SHIP_ZIP = "shipZip";
      }
    }
  }

  protected interface SecretsConstant {
    String API_KEY = "SENDGRID_API_KEY";
    String RECEIVER_EMAIL = "RECEIVER_EMAIL";
    String RECEIVER_NAME = "RECEIVER_NAME";
    String SENDER_EMAIL = "SENDER_EMAIL";
    String SENDER_NAME = "SENDER_NAME";

    interface Content {
      String SUBJECT = "CONTENT_SUBJECT";
      String TYPE = "CONTENT_TYPE";
      String VALUE = "CONTENT_VALUE";
    }

    interface Template {
      String ID = "TEMPLATE_ID";

      interface Data {
        String ACCOUNT_NAME = "TEMPLATE_DATA_ACCOUNT_NAME";
        String SHIP_ADDRESS = "TEMPLATE_DATA_SHIP_ADDRESS";
        String SHIP_ZIP = "TEMPLATE_DATA_SHIP_ZIP";
      }
    }
  }

  protected static Stream<String> failRequestCases() throws IOException {
    return loadTestCasesFromResourceFile(FAIL_REQUEST_CASES_PATH);
  }

  protected static Stream<String> successReplaceSecretsRequestCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_REPLACE_SECRETS_REQUEST_CASES_PATH);
  }

  protected static Stream<String> successReplaceSecretsContentRequestCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_REPLACE_SECRETS_CONTENT_REQUEST_CASES_PATH);
  }

  protected static Stream<String> successReplaceSecretsTemplateRequestCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_REPLACE_SECRETS_TEMPLATE_REQUEST_CASES_PATH);
  }

  protected static Stream<String> successSendMailByTemplateRequestCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_SEND_MAIL_BY_TEMPLATE_TEMPLATE_REQUEST_CASES_PATH);
  }

  protected static Stream<String> successSendMailWithContentRequestCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_SEND_MAIL_WITH_CONTENT_REQUEST_CASES_PATH);
  }

  protected static Stream<String> failTestWithWrongSenderEmail() throws IOException {
    return loadTestCasesFromResourceFile(FAIL_REQUEST_WITH_WRONG_SENDER_EMAIL);
  }

  protected static Stream<String> failTestWithWrongSenderName() throws IOException {
    return loadTestCasesFromResourceFile(FAIL_REQUEST_WITH_WRONG_SENDER_NAME);
  }

  protected static Stream<String> failTestWithWrongReceiverEmail() throws IOException {
    return loadTestCasesFromResourceFile(FAIL_REQUEST_WITH_WRONG_RECEIVER_EMAIL);
  }

  protected static Stream<String> failTestWithWrongReceiverName() throws IOException {
    return loadTestCasesFromResourceFile(FAIL_REQUEST_WITH_WRONG_RECEIVER_NAME);
  }

  protected static OutboundConnectorContextBuilder getContextBuilderWithSecrets() {
    return OutboundConnectorContextBuilder.create()
        .secret(SecretsConstant.API_KEY, ActualValue.API_KEY)
        .secret(SecretsConstant.SENDER_EMAIL, ActualValue.SENDER_EMAIL)
        .secret(SecretsConstant.SENDER_NAME, ActualValue.SENDER_NAME)
        .secret(SecretsConstant.RECEIVER_EMAIL, ActualValue.RECEIVER_EMAIL)
        .secret(SecretsConstant.RECEIVER_NAME, ActualValue.RECEIVER_NAME)
        .secret(SecretsConstant.Content.SUBJECT, ActualValue.Content.SUBJECT)
        .secret(SecretsConstant.Content.TYPE, ActualValue.Content.TYPE)
        .secret(SecretsConstant.Content.VALUE, ActualValue.Content.VALUE)
        .secret(SecretsConstant.Template.ID, ActualValue.Template.ID)
        .secret(SecretsConstant.Template.Data.ACCOUNT_NAME, ActualValue.Template.Data.ACCOUNT_NAME)
        .secret(SecretsConstant.Template.Data.SHIP_ADDRESS, ActualValue.Template.Data.SHIP_ADDRESS)
        .secret(SecretsConstant.Template.Data.SHIP_ZIP, ActualValue.Template.Data.SHIP_ZIP);
  }

  @SuppressWarnings("unchecked")
  protected static Stream<String> loadTestCasesFromResourceFile(final String fileWithTestCasesUri)
      throws IOException {
    final String cases = readString(new File(fileWithTestCasesUri).toPath(), UTF_8);
    final Gson testingGson = new Gson();
    var array = testingGson.fromJson(cases, ArrayList.class);
    return array.stream().map(testingGson::toJson).map(Arguments::of);
  }
}
