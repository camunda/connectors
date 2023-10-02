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
package io.camunda.connector.runtime.core.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.inbound.correlation.MessageCorrelationPoint;
import io.camunda.connector.feel.ConnectorsObjectMapperSupplier;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class InboundConnectorDefinitionImplTest {

  @Test
  void rawProperties_notSerializedAsJson() throws JsonProcessingException {
    // given
    var testObj =
        new InboundConnectorDefinitionImpl(
            Map.of("auth", "abc"), new MessageCorrelationPoint("", "", null), "", 0, 0L, "", "");

    // when
    var result = ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.writeValueAsString(testObj);

    // then
    assertThat(result).doesNotContain("auth", "abc");
  }

  @Test
  void rawProperties_notPartOfToString() {
    // given
    var testObj =
        new InboundConnectorDefinitionImpl(
            Map.of("auth", "abc"), new MessageCorrelationPoint("", "", null), "", 0, 0L, "", "");

    // when
    var result = testObj.toString();

    // then
    assertThat(result).doesNotContain("auth", "abc");
  }
}
