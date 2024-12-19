/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.model;

import java.util.List;
import java.util.Objects;

public class BedrockMessage {

  private String role;

  private List<BedrockContent> contentList;

  public BedrockMessage(String role, List<BedrockContent> contentList) {
    this.role = role;
    this.contentList = contentList;
  }

  public BedrockMessage() {}

  public String getRole() {
    return role;
  }

  public List<BedrockContent> getContentList() {
    return contentList;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public void setContentList(List<BedrockContent> contentList) {
    this.contentList = contentList;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BedrockMessage that = (BedrockMessage) o;
    return Objects.equals(role, that.role) && Objects.equals(contentList, that.contentList);
  }

  @Override
  public int hashCode() {
    return Objects.hash(role, contentList);
  }
}
