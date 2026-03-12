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
package io.camunda.connector.feel.jackson;

import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import io.camunda.client.CamundaClient;
import io.camunda.connector.api.annotation.FEEL;
import java.util.function.Supplier;

public class FeelAnnotationIntrospector extends JacksonAnnotationIntrospector {

  private final Supplier<CamundaClient> camundaClientSupplier;

  /** Creates an introspector that uses local FEEL engine. */
  public FeelAnnotationIntrospector() {
    this(null);
  }

  /**
   * Creates an introspector with optional CamundaClient for remote FEEL evaluation.
   *
   * @param camundaClientSupplier supplier for CamundaClient, or null to use local FEEL engine
   */
  public FeelAnnotationIntrospector(Supplier<CamundaClient> camundaClientSupplier) {
    this.camundaClientSupplier = camundaClientSupplier;
  }

  @Override
  public Object findDeserializer(Annotated a) {
    FEEL ann = _findAnnotation(a, FEEL.class);
    if (ann != null) {
      return new FeelDeserializer(camundaClientSupplier);
    }
    return super.findDeserializer(a);
  }
}
