/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.resolver;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.adhoctoolsschema.feel.FeelInputParamExtractor;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.schema.AdHocToolSchemaGenerator;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CamundaClientAdHocToolsSchemaResolverTest {

  @Mock private CamundaClient camundaClient;
  @Mock private FeelInputParamExtractor feelInputParamExtractor;
  @Mock private AdHocToolSchemaGenerator schemaGenerator;
  @InjectMocks private CamundaClientAdHocToolsSchemaResolver resolver;

  @ParameterizedTest
  @NullSource
  @ValueSource(longs = {0, -10})
  void throwsExceptionWhenProcessDefinitionKeyIsInvalid(Long processDefinitionKey) {
    assertThatThrownBy(() -> resolver.resolveSchema(processDefinitionKey, "AHSP"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Process definition key must not be null or negative");

    verifyNoInteractions(camundaClient, feelInputParamExtractor, schemaGenerator);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void throwsExceptionWhenAdHocSubprocessIdIsInvalid(String adHocSubprocessId) {
    assertThatThrownBy(() -> resolver.resolveSchema(123456L, adHocSubprocessId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("adHocSubprocessId cannot be null or empty");

    verifyNoInteractions(camundaClient, feelInputParamExtractor, schemaGenerator);
  }
}
