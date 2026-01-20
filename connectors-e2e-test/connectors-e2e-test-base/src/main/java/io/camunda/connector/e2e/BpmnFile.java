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

import static org.springframework.test.util.AssertionErrors.assertTrue;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

public class BpmnFile {

  private final BpmnModelInstance bpmnModelInstance;
  private File bpmnFile;

  public BpmnFile(BpmnModelInstance bpmnModelInstance) {
    this.bpmnModelInstance = bpmnModelInstance;
  }

  public BpmnFile(File bpmnFile) {
    this.bpmnModelInstance = Bpmn.readModelFromFile(bpmnFile);
    this.bpmnFile = bpmnFile;
  }

  public static BpmnModelInstance replace(String resourceName, Replace... replaces) {
    try {
      var resource = BpmnFile.class.getClassLoader().getResource(resourceName);
      var file = new File(resource.toURI());
      return replace(file, replaces);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static BpmnModelInstance replace(File file, Replace... replaces) {
    try {
      var modelXml = IOUtils.toString(file.toURI(), StandardCharsets.UTF_8);
      for (var replace : replaces) {
        modelXml = modelXml.replaceAll(replace.oldValue, replace.newValue);
      }
      return Bpmn.readModelFromStream(IOUtils.toInputStream(modelXml, StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public BpmnFile writeToFile(File file) {
    bpmnFile = file;
    Bpmn.writeModelToFile(bpmnFile, bpmnModelInstance);
    return this;
  }

  public BpmnModelInstance apply(File template, String elementId, File output) {
    assertTrue("BPMN file must be written to disk: " + bpmnFile, bpmnFile.exists());
    try {
      final var process =
          new ProcessBuilder()
              .command(
                  "element-templates-cli",
                  "--diagram",
                  bpmnFile.getPath(),
                  "--template",
                  template.getPath(),
                  "--element",
                  elementId,
                  "--output",
                  output.getPath())
              .start();

      final var exitCode = process.waitFor();
      if (exitCode != 0) {
        var stdout = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
        var stderr = IOUtils.toString(process.getErrorStream(), StandardCharsets.UTF_8);

        var exceptionMessage =
            "Failed to apply element template via element-templates-cli. Exit code: " + exitCode;

        if (StringUtils.isNotBlank(stdout)) {
          exceptionMessage += "\nSTDOUT: " + stdout;
        }

        if (StringUtils.isNotBlank(stderr)) {
          exceptionMessage += "\nSTDERR: " + stderr;
        }

        throw new RuntimeException(exceptionMessage);
      }

      return Bpmn.readModelFromFile(output);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public BpmnModelInstance getBpmnModelInstance() {
    return bpmnModelInstance;
  }

  public record Replace(String oldValue, String newValue) {
    public static Replace replace(String oldValue, String newValue) {
      return new Replace(oldValue, newValue);
    }
  }
}
