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
package io.camunda.connector.runtime.core.feel.jackson.function;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.camunda.connector.runtime.core.feel.FeelEngineWrapper;
import java.util.function.Function;
import java.util.function.Supplier;

public class JacksonModuleFeelFunction extends SimpleModule {

  private final FeelEngineWrapper feelEngineWrapper = new FeelEngineWrapper();

  @Override
  public String getModuleName() {
    return "JacksonModuleFeelFunction";
  }

  @Override
  public Version version() {
    // TODO: extract module into a separate artifact and get version dynamically from pom.xml
    return new Version(0, 1, 0, null, "io.camunda", "jackson-module-feel");
  }

  @Override
  public void setupModule(SetupContext context) {
    addDeserializer(
        Function.class, new FeelFunctionDeserializer<>(Object.class, feelEngineWrapper));
    addDeserializer(
        Supplier.class, new FeelSupplierDeserializer<>(Object.class, feelEngineWrapper));
    super.setupModule(context);
  }
}
