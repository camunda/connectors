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
package io.camunda.connector.runtime.test.document;

import io.camunda.connector.api.document.DocumentMetadata;
import java.time.OffsetDateTime;
import java.util.Map;

public class TestDocumentMetadata implements DocumentMetadata {

  private final String contentType;
  private final OffsetDateTime expiresAt;
  private final Long size;
  private final String fileName;
  private final String processDefinitionId;
  private final Long processInstanceKey;
  private final Map<String, Object> customProperties;

  public TestDocumentMetadata(TestDocumentMetadata apiResponse) {
    this.contentType = apiResponse.getContentType();
    this.expiresAt = apiResponse.getExpiresAt();
    this.size = apiResponse.getSize();
    this.fileName = apiResponse.getFileName();
    this.processDefinitionId = apiResponse.getProcessDefinitionId();
    this.processInstanceKey = apiResponse.getProcessInstanceKey();
    this.customProperties = apiResponse.getCustomProperties();
  }

  public TestDocumentMetadata(
      String contentType,
      OffsetDateTime expiresAt,
      Long size,
      String fileName,
      String processDefinitionId,
      Long processInstanceKey,
      Map<String, Object> customProperties) {
    this.contentType = contentType;
    this.expiresAt = expiresAt;
    this.size = size;
    this.fileName = fileName;
    this.processDefinitionId = processDefinitionId;
    this.processInstanceKey = processInstanceKey;
    this.customProperties = customProperties;
  }

  @Override
  public String getContentType() {
    return contentType;
  }

  @Override
  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  @Override
  public Long getSize() {
    return size;
  }

  @Override
  public String getFileName() {
    return fileName;
  }

  @Override
  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  @Override
  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public Map<String, Object> getCustomProperties() {
    return customProperties;
  }
}
