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

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.camunda.client.CamundaClient;
import io.camunda.connector.feel.FeelEngineWrapper;
import java.util.function.Function;
import java.util.function.Supplier;

public class JacksonModuleFeelFunction extends SimpleModule {

  private final FeelEngineWrapper feelEngineWrapper = new FeelEngineWrapper();

  /**
   * Using this flag, the module can be configured to not process the {@code @FEEL} annotation. This
   * can be useful in scenarios where only deserialization of Function/Supplier is needed, but not
   * the annotation processing (e.g., to avoid interference with other modules). This way, we can
   * use the same models with (inbound connectors) and without (outbound connectors) FEEL support.
   */
  private final boolean processFEELAnnotation;

  private final Supplier<CamundaClient> camundaClientSupplier;

  /** Creates a module using local FEEL engine. */
  public JacksonModuleFeelFunction() {
    this(true, null);
  }

  /**
   * Creates a module with optional CamundaClient for remote FEEL evaluation.
   *
   * @param processFEELAnnotation whether to process @FEEL annotations
   * @param camundaClientSupplier supplier for CamundaClient, or null to use local FEEL engine
   */
  public JacksonModuleFeelFunction(
      boolean processFEELAnnotation, Supplier<CamundaClient> camundaClientSupplier) {
    this.processFEELAnnotation = processFEELAnnotation;
    this.camundaClientSupplier = camundaClientSupplier;
  }

  @Override
  public String getModuleName() {
    return "JacksonModuleFeelFunction";
  }

  @Override
  public Version version() {
    // TODO: get version from pom.xml
    return new Version(0, 1, 0, null, "io.camunda", "jackson-datatype-feel");
  }

  @Override
  public void setupModule(SetupContext context) {
    addDeserializer(
        Function.class,
        new FeelFunctionDeserializer<>(
            TypeFactory.unknownType(), feelEngineWrapper, camundaClientSupplier));
    addDeserializer(
        Supplier.class,
        new FeelSupplierDeserializer<>(
            TypeFactory.unknownType(), feelEngineWrapper, camundaClientSupplier));
    if (processFEELAnnotation) {
      context.insertAnnotationIntrospector(new FeelAnnotationIntrospector(camundaClientSupplier));
    }
    super.setupModule(context);
  }
}
