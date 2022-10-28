/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;

import com.google.gson.Gson;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public abstract class BaseTest {

  private static final String FAIL_REQUEST_VALIDATE_REQUIRED_FIELD_CASES_PATH =
      "src/test/resources/requests/fail-test-cases-validation-required-fields.json";

  private static final String FAIL_REQUEST_WITH_WRONG_PROPERTIES_FIELDS_CASES_PATH =
      "src/test/resources/requests/fail-test-cases-bad-message-properties.json";

  private static final String FAIL_PROPERTIES_FIELDS_VALIDATION_TEST_CASES_PATH =
      "src/test/resources/requests/fail-test-cases-properties-object-validation.json";

  private static final String SUCCESS_REQUEST_EXECUTE_CASES_PATH =
      "src/test/resources/requests/success-test-cases-execute-function.json";

  private static final String SUCCESS_REPLACE_SECRETS_TEST_CASES_PATH =
      "src/test/resources/requests/success-test-cases-replace-secrets.json";

  protected Gson gson = new Gson();

  protected interface ActualValue {

    interface Authentication {
      String USERNAME = "testUserName";
      String PASSWORD = "testPassword";
      String URI = "amqp://userName:password@localhost:5672/vhost";
    }

    interface Routing {
      String VIRTUAL_HOST = "virtualHostName";
      String HOST_NAME = "localhost";
      String PORT = "5672";
      String EXCHANGE = "testExchangeName";
      String ROUTING_KEY = "testRoutingKeyName";
    }

    interface Message {
      interface Body {
        String BODY_KEY = "msg_key";
        String VALUE = "replaced text";
      }

      interface Properties {
        String CONTENT_TYPE = "text/plan";
        String CONTENT_ENCODING = "UTF-8";

        interface Headers {
          String HEADER_KEY = "header1";
          String HEADER_VALUE = "headerValue";
        }
      }
    }
  }

  protected interface SecretsConstant {

    String SECRETS = "secrets.";

    interface Authentication {
      String USERNAME = "USERNAME_KEY";
      String PASSWORD = "PASSWORD_KEY";
      String URI = "URI_KEY";
      String CREDENTIALS = "CREDENTIALS_KEY";
    }

    interface Routing {
      String VIRTUAL_HOST = "VIRTUAL_HOST_KEY";
      String HOST_NAME = "HOST_NAME_KEY";
      String PORT = "PORT_KEY";
      String EXCHANGE = "EXCHANGE_NAME_KEY";
      String ROUTING_KEY = "ROUTING_SECRET_KEY";
    }

    interface Message {
      interface Body {
        String VALUE = "TEXT_KEY";
      }

      interface Properties {
        String CONTENT_TYPE = "CONTENT_TYPE_KEY";
        String CONTENT_ENCODING = "CONTENT_ENCODING_KEY";

        interface Headers {
          String HEADER_KEY = "HEADER_KEY";
          String HEADER_VALUE = "HEADER_VALUE_KEY";
        }
      }
    }
  }

  protected static OutboundConnectorContextBuilder getContextBuilderWithSecrets() {
    return OutboundConnectorContextBuilder.create()
        .secret(SecretsConstant.Authentication.USERNAME, ActualValue.Authentication.USERNAME)
        .secret(SecretsConstant.Authentication.PASSWORD, ActualValue.Authentication.PASSWORD)
        .secret(SecretsConstant.Authentication.URI, ActualValue.Authentication.URI)
        .secret(SecretsConstant.Routing.HOST_NAME, ActualValue.Routing.HOST_NAME)
        .secret(SecretsConstant.Routing.VIRTUAL_HOST, ActualValue.Routing.VIRTUAL_HOST)
        .secret(SecretsConstant.Routing.ROUTING_KEY, ActualValue.Routing.ROUTING_KEY)
        .secret(SecretsConstant.Routing.EXCHANGE, ActualValue.Routing.EXCHANGE)
        .secret(SecretsConstant.Routing.PORT, ActualValue.Routing.PORT)
        .secret(SecretsConstant.Message.Body.VALUE, ActualValue.Message.Body.VALUE)
        .secret(
            SecretsConstant.Message.Properties.CONTENT_ENCODING,
            ActualValue.Message.Properties.CONTENT_ENCODING)
        .secret(
            SecretsConstant.Message.Properties.CONTENT_TYPE,
            ActualValue.Message.Properties.CONTENT_TYPE)
        .secret(
            SecretsConstant.Message.Properties.Headers.HEADER_KEY,
            ActualValue.Message.Properties.Headers.HEADER_KEY)
        .secret(
            SecretsConstant.Message.Properties.Headers.HEADER_VALUE,
            ActualValue.Message.Properties.Headers.HEADER_VALUE)
        .secret(
            SecretsConstant.Message.Properties.Headers.HEADER_VALUE,
            ActualValue.Message.Properties.Headers.HEADER_VALUE);
  }

  protected static Stream<String> failValidationRequiredFieldsTest() throws IOException {
    return loadTestCasesFromResourceFile(FAIL_REQUEST_VALIDATE_REQUIRED_FIELD_CASES_PATH);
  }

  protected static Stream<String> failExecuteConnectorWithWrongPropertiesFields()
      throws IOException {
    return loadTestCasesFromResourceFile(FAIL_REQUEST_WITH_WRONG_PROPERTIES_FIELDS_CASES_PATH);
  }

  protected static Stream<String> failPropertiesFieldValidationTest() throws IOException {
    return loadTestCasesFromResourceFile(FAIL_PROPERTIES_FIELDS_VALIDATION_TEST_CASES_PATH);
  }

  protected static Stream<String> successExecuteConnectorTest() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_REQUEST_EXECUTE_CASES_PATH);
  }

  protected static Stream<String> successReplaceSecretsTest() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_REPLACE_SECRETS_TEST_CASES_PATH);
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
