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
package io.camunda.connector.runtime.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.connector.runtime.core.intrinsic.DefaultIntrinsicFunctionExecutor;
import io.camunda.connector.runtime.core.outbound.ConnectorJobHandler;
import io.camunda.connector.runtime.utils.TestSecretProvider;
import io.camunda.connector.runtime.utils.TestValidation;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import org.junit.jupiter.api.Test;

public class ConnectorJobHandlerTests {

  static DocumentFactory documentFactory = new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  private static ObjectMapper createObjectMapper() {
    var copy = ConnectorsObjectMapperSupplier.getCopy();
    var functionExecutor = new DefaultIntrinsicFunctionExecutor(copy);
    var jacksonModuleDocumentDeserializer =
        new JacksonModuleDocumentDeserializer(
            documentFactory, functionExecutor, DocumentModuleSettings.create());
    return copy.registerModules(
        jacksonModuleDocumentDeserializer,
        new JacksonModuleFeelFunction(),
        new JacksonModuleDocumentSerializer());
  }

  protected static ConnectorJobHandler newConnectorJobHandler(OutboundConnectorFunction call) {

    return new ConnectorJobHandler(
        call,
        new TestSecretProvider(),
        new DefaultValidationProvider(),
        documentFactory,
        createObjectMapper());
  }

  @Test
  void shouldRaiseExceptionDuringJsonProcessing() {
    // given
    var jobHandler = newConnectorJobHandler(context -> context.bindVariables(TestValidation.class));

    // when
    var result =
        JobBuilder.create()
            .withVariables("{ \"test\" : \"{{secrets.FOO}}\", \"test2\" : \"{{secrets.FOO}}\" }")
            .executeAndCaptureResult(jobHandler, false);

    // then
    assertThat(result.getErrorMessage())
        .isEqualTo(
            "jakarta.validation.ValidationException: Found constraints violated while validating input: \n"
                + " - Property: test: Validation failed. Original message: numeric value out of bounds (<2 digits>.<0 digits> expected)");
  }

  @Test
  void shouldPrioritizeSecretNotFoundException() {
    // given
    var jobHandler = newConnectorJobHandler(context -> context.bindVariables(TestValidation.class));

    // when
    var result =
        JobBuilder.create()
            .withVariables("{ \"test\" : \"{{secrets.FOO}}\", \"test2\" : \"{{secrets.FOO2}}\" }")
            .executeAndCaptureResult(jobHandler, false);

    // then
    assertThat(result.getErrorMessage()).isEqualTo("Secret with name 'FOO2' is not available");
  }

  @Test
  void shouldRaiseMultipleExceptionsDuringJsonProcessing() {
    // given
    var jobHandler = newConnectorJobHandler(context -> context.bindVariables(TestValidation.class));

    // when
    var result =
        JobBuilder.create()
            .withVariables("{ \"test\" : \"{{secrets.FOO}}\", \"test2\" : \"\" }")
            .executeAndCaptureResult(jobHandler, false);

    // then
    assertThat(result.getErrorMessage())
        .contains("Property: test2: Validation failed. Original message: must not be empty");
    assertThat(result.getErrorMessage())
        .contains("Property: test2: Validation failed. Original message: must not be empty");
  }

  @Test
  void connectorRaiseAnExceptionContainingSecret() {
    // given
    var jobHandler =
        newConnectorJobHandler(
            context -> {
              throw new ConnectorException("test: bar");
            });

    // when
    var result =
        JobBuilder.create()
            .withVariables("{ \"test\" : \"{{secrets.FOO}}\", \"test2\" : \"null\" }")
            .executeAndCaptureResult(jobHandler, false);

    // then
    assertThat(result.getErrorMessage()).isEqualTo("test: ***");
  }

  @Test
  void retrieveAllSecretsShouldNotThrowIfSecretNotFound() {
    // given
    var jobHandler =
        newConnectorJobHandler(
            context -> {
              throw new ConnectorException("test: bar");
            });

    // when
    var result =
        JobBuilder.create()
            .withVariables("{ \"test\" : \"12\", \"test2\" : \"{{secrets.FOO}}\" }")
            .executeAndCaptureResult(jobHandler, false);

    // then
    assertThat(result.getErrorMessage()).isEqualTo("test: ***");
  }
}
