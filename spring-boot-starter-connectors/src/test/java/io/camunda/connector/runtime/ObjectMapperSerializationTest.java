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
package io.camunda.connector.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.spring.test.ZeebeSpringTest;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.polling.enabled=false"
    })
@ZeebeSpringTest
public class ObjectMapperSerializationTest {

  @Autowired private JsonMapper jsonMapper;
  @Autowired private ApplicationContext applicationContext;

  @Test
  void getJsonMapper() throws JsonProcessingException {
    ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper.class);
    assertThat(objectMapper.writeValueAsString(new Date().toInstant().atOffset(ZoneOffset.UTC)))
        .isNotNull();

    assertThat(jsonMapper).isNotNull();
    Map<String, JsonMapper> jsonMapperBeans = applicationContext.getBeansOfType(JsonMapper.class);
    Object objectMapperOfJsonMapper = ReflectionTestUtils.getField(jsonMapper, "objectMapper");
    assertEquals(objectMapper, objectMapperOfJsonMapper);

    assertThat(jsonMapperBeans.size()).isEqualTo(1);
    assertThat(jsonMapperBeans.containsKey("zeebeJsonMapper")).isTrue();
    assertThat(jsonMapperBeans.get("zeebeJsonMapper")).isSameAs(jsonMapper);

    assertThat(objectMapper).isNotNull();
    assertThat(objectMapper).isInstanceOf(ObjectMapper.class);
    assertThat(objectMapper.getDeserializationConfig()).isNotNull();
    assertThat(
            objectMapper
                .getDeserializationConfig()
                .isEnabled(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES))
        .isFalse();
    assertThat(
            objectMapper
                .getDeserializationConfig()
                .isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES))
        .isFalse();
    // should serialise OffsetDateTime
    assertThat(jsonMapper.toJson(new Date().toInstant().atOffset(ZoneOffset.UTC))).isNotNull();
  }
}
