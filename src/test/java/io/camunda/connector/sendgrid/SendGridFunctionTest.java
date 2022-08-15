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
package io.camunda.connector.sendgrid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.test.ConnectorContextBuilder;
import java.io.IOException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class SendGridFunctionTest extends BaseTest {

  private ConnectorContext context;

  @BeforeEach
  public void init() {
    context =
        ConnectorContextBuilder.create()
            .secret(API_KEY, ACTUAL_API_KEY)
            .variables(new SendGridRequest())
            .build();
  }

  @Test
  public void execute_shouldReturnErrorWhenStatusCodeNotOK() throws IOException {

    // given
    SendGridFunction sendGridFunction = new SendGridFunction();
    Response sendGridResponse = new Response();
    sendGridResponse.setStatusCode(202);

    SendGrid sendGrid = Mockito.mock(SendGrid.class);
    when(sendGrid.api(any(com.sendgrid.Request.class))).thenReturn(sendGridResponse);

    // when
    Throwable exceptionThrown =
        Assertions.assertThrows(RuntimeException.class, () -> sendGridFunction.execute(context));

    // then
    assertThat(exceptionThrown).isInstanceOf(IllegalArgumentException.class);
  }
}
