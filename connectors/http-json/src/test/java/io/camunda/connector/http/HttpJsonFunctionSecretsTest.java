/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.http;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.api.ConnectorInput;
import io.camunda.connector.http.auth.BearerAuthentication;
import io.camunda.connector.http.model.HttpJsonRequest;
import io.camunda.connector.test.ConnectorContextBuilder;
import java.io.IOException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class HttpJsonFunctionSecretsTest {

  private static final String DUMMY_REQUEST =
      "{\n"
          + "    \"method\": \"get\",\n"
          + "    \"url\": \"secrets.MY_SECRET_URL\",\n"
          + "    \"authentication\": {\n"
          + "      \"type\": \"bearer\",\n"
          + "      \"token\": \"secrets.MY_TOKEN\"\n"
          + "      }\n"
          + "  }";

  private ArgumentCaptor<ConnectorInput> captor = ArgumentCaptor.forClass(ConnectorInput.class);

  private HttpJsonFunction functionUnderTest;

  @BeforeEach
  void setup() {
    functionUnderTest = new HttpJsonFunction();
  }

  @Test
  void shouldChangeSecret_WhenSecretSupplied() throws IOException {
    // Given
    final String secretUrl = "https://camunda.io/secretUrl";
    final String token = "s3cr3tT0k3n";
    ConnectorContext ctx =
        Mockito.spy(
            ConnectorContextBuilder.create()
                .variables(DUMMY_REQUEST)
                .secret("MY_TOKEN", token)
                .secret("MY_SECRET_URL", secretUrl)
                .build());

    // When
    functionUnderTest.execute(ctx);

    // Then
    Mockito.verify(ctx).replaceSecrets(captor.capture());
    ConnectorInput input = captor.getValue();
    Assertions.assertThat(input).isInstanceOf(HttpJsonRequest.class);
    HttpJsonRequest inputAsHttpJsonRequest = (HttpJsonRequest) input;
    Assertions.assertThat(inputAsHttpJsonRequest.getAuthentication())
        .isInstanceOf(BearerAuthentication.class);
    BearerAuthentication auth = (BearerAuthentication) inputAsHttpJsonRequest.getAuthentication();
    Assertions.assertThat(auth.getToken()).isEqualTo(token);
  }

  @Test
  void shouldWhat_WhenSecretNotSupplied() {
    ConnectorContext ctx =
        Mockito.spy(ConnectorContextBuilder.create().variables(DUMMY_REQUEST).build());

    Throwable exception =
        assertThrows(IllegalArgumentException.class, () -> functionUnderTest.execute(ctx));

    Assertions.assertThat(exception.getMessage())
        .contains("Secret with name 'MY_SECRET_URL' is not available");
  }
}
