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
package io.camunda.connector.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.aws.model.impl.AwsAuthentication.AwsStaticCredentialsAuthentication;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.feel.jackson.FeelContextAwareObjectReader;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;
import io.camunda.connector.inbound.model.SqsInboundProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SqsInboundPropertiesTest {

  @Test
  void bindsCredentialExpressionThroughFeelReader() throws Exception {
    String expression = "=camunda.vars.env.sqsCredential";
    var credential =
        Map.of(
            "authentication",
            Map.of("type", "credentials", "accessKey", "cred-ak", "secretKey", "cred-sk"),
            "region",
            "eu-west-1");
    var evaluator = mock(FeelExpressionEvaluator.class);
    when(evaluator.evaluate(eq(expression), any(Object[].class))).thenReturn(credential);
    var objectMapper =
        ObjectMapperSupplier.getMapperInstance()
            .copy()
            .registerModule(new JacksonModuleFeelFunction());
    JsonNode propertiesJson = objectMapper.valueToTree(Map.of("awsCredential", expression));

    var properties =
        FeelContextAwareObjectReader.of(objectMapper)
            .withEvaluator(evaluator)
            .readValue(propertiesJson, SqsInboundProperties.class);

    assertThat(properties.getAuthentication())
        .isInstanceOf(AwsStaticCredentialsAuthentication.class);
    assertThat(((AwsStaticCredentialsAuthentication) properties.getAuthentication()).accessKey())
        .isEqualTo("cred-ak");
    assertThat(properties.getConfiguration().region()).isEqualTo("eu-west-1");
  }
}
