/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.feel.ConnectorsObjectMapperSupplier;
import io.camunda.connector.rabbitmq.supplier.ObjectMapperSupplier;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public abstract class BaseTest {

  protected ObjectMapper objectMapper = ObjectMapperSupplier.instance();

  public interface ActualValue {

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

    String QUEUE_NAME = "testQueueName";
    String CONSUMER_TAG = "testConsumerTag";
    String QUEUE_TYPE = "quorum";
  }

  protected interface SecretsConstant {

    String SECRETS = "secrets.";

    String QUEUE_NAME = "QUEUE_NAME";
    String CONSUMER_TAG = "CONSUMER_TAG";
    String QUEUE_TYPE = "QUEUE_TYPE";

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

  @SuppressWarnings("unchecked")
  protected static Stream<String> loadTestCasesFromResourceFile(final String fileWithTestCasesUri)
      throws IOException {
    final String cases = readString(new File(fileWithTestCasesUri).toPath(), UTF_8);
    final ObjectMapper mapper = ConnectorsObjectMapperSupplier.getCopy();
    var array = mapper.readValue(cases, ArrayList.class);
    return array.stream()
        .map(
            value -> {
              try {
                return mapper.writeValueAsString(value);
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            })
        .map(Arguments::of);
  }
}
