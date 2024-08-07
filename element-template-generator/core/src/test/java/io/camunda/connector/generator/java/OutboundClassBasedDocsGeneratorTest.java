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
package io.camunda.connector.generator.java;

import io.camunda.connector.generator.BaseTest;
import io.camunda.connector.generator.api.DocsGeneratorConfiguration;
import io.camunda.connector.generator.java.example.outbound.MyConnectorFunction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.io.PrintWriter;

public class OutboundClassBasedDocsGeneratorTest extends BaseTest {

  private final ClassBasedDocsGenerator generator = new ClassBasedDocsGenerator();

  @Nested
  class Basic {

    @Test
    void elementType_default_isServiceTask() {
      DocsGeneratorConfiguration config =
          new DocsGeneratorConfiguration(
                  "templates/connector-doc.md.peb", "test.md");
      var result =
          generator.generate(MyConnectorFunction.FullyAnnotated.class, config).getFirst().content();
      System.out.println(result);

//      try (PrintWriter out = new PrintWriter(config.outputPath())) {
//        out.println(result);
//      } catch (FileNotFoundException e) {
//          throw new RuntimeException(e);
//      }
    }
  }
}
