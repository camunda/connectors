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

package io.camunda.connector.api.document;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

public record DocumentCreationRequest(
    InputStream content,
    String documentId,
    String storeId,
    String contentType,
    String fileName,
    Duration timeToLive,
    String processDefinitionId,
    Long processInstanceKey,
    Map<String, Object> customProperties) {

  public static BuilderFinalStep from(InputStream content) {
    return new BuilderFinalStep(content);
  }

  public static BuilderFinalStep from(byte[] content) {
    return new BuilderFinalStep(new ByteArrayInputStream(content));
  }

  public static class BuilderFinalStep {

    private final InputStream content;
    private String documentId;
    private String storeId;
    private String contentType;
    private String fileName;
    private Duration timeToLive;
    private String processDefinitionId;
    private Long processInstanceKey;
    private Map<String, Object> customProperties;

    public BuilderFinalStep(InputStream content) {
      this.content = content;
    }

    public BuilderFinalStep documentId(String documentId) {
      this.documentId = documentId;
      return this;
    }

    public BuilderFinalStep storeId(String storeId) {
      this.storeId = storeId;
      return this;
    }

    public BuilderFinalStep contentType(String contentType) {
      this.contentType = contentType;
      return this;
    }

    public BuilderFinalStep fileName(String fileName) {
      this.fileName = fileName;
      return this;
    }

    public BuilderFinalStep timeToLive(Duration timeToLive) {
      this.timeToLive = timeToLive;
      return this;
    }

    public BuilderFinalStep processDefinitionId(String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    public BuilderFinalStep processInstanceKey(Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public BuilderFinalStep customProperties(Map<String, Object> customProperties) {
      this.customProperties = customProperties;
      return this;
    }

    public DocumentCreationRequest build() {
      return new DocumentCreationRequest(
          content,
          documentId,
          storeId,
          contentType,
          fileName,
          timeToLive,
          processDefinitionId,
          processInstanceKey,
          customProperties);
    }
  }
}
