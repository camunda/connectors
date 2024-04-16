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
package io.camunda.connector.generator;

import java.util.List;
import java.util.Map;

public class ConnectorConfig {

  private String connectorClass;

  private boolean generateHybridTemplates = false;

  private List<FileNameById> files = List.of();

  private Map<String, Boolean> features = Map.of();

  public static class FileNameById {
    private String templateId;
    private String templateFileName;

    public FileNameById() {}

    public String getTemplateId() {
      return templateId;
    }

    public void setTemplateId(String id) {
      this.templateId = id;
    }

    public String getTemplateFileName() {
      return templateFileName;
    }

    public void setTemplateFileName(String templateFileName) {
      this.templateFileName = templateFileName;
    }
  }

  public ConnectorConfig() {}

  public String getConnectorClass() {
    return connectorClass;
  }

  public void setConnectorClass(String connectorClass) {
    this.connectorClass = connectorClass;
  }

  public boolean isGenerateHybridTemplates() {
    return generateHybridTemplates;
  }

  public List<FileNameById> getFiles() {
    return files;
  }

  public void setFiles(List<FileNameById> files) {
    this.files = files;
  }

  public void setGenerateHybridTemplates(boolean generateHybridTemplates) {
    this.generateHybridTemplates = generateHybridTemplates;
  }

  public Map<String, Boolean> getFeatures() {
    return features;
  }

  public void setFeatures(Map<String, Boolean> features) {
    this.features = features;
  }
}
