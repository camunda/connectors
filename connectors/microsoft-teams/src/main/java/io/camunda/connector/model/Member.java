/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model;

import com.google.gson.JsonPrimitive;
import io.camunda.connector.api.annotation.Secret;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotNull;

public class Member {

  public static final String USER_DATA_BIND = "user@odata.bind";
  public static final String USER_DATA_TYPE = "@odata.type";
  public static final JsonPrimitive USER_CONVERSATION_MEMBER =
      new JsonPrimitive("#microsoft.graph.aadUserConversationMember");
  public static final List<String> OWNER_ROLES = List.of("owner");

  @Secret private String userId;
  @Secret private String userPrincipalName;
  @Secret @NotNull private List<String> roles;

  @AssertTrue(message = "Missing one of properties : [userId, userPrincipalName]")
  private boolean isUserOrNameExist() {
    return userId != null
        && !userId.isBlank()
        && userPrincipalName != null
        && !userPrincipalName.isBlank();
  }

  public JsonPrimitive getAsGraphJsonPrimitive() {
    return new JsonPrimitive(
        "https://graph.microsoft.com/v1.0/users('"
            + Optional.ofNullable(userId).orElse(userPrincipalName)
            + "')");
  }

  public static JsonPrimitive toGraphJsonPrimitive(final String user) {
    return new JsonPrimitive(
        "https://graph.microsoft.com/v1.0/users('"
            + Optional.ofNullable(user)
                .orElseThrow(
                    () -> new NullPointerException("Must be userId or user principal name"))
            + "')");
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(final String userId) {
    this.userId = userId;
  }

  public List<String> getRoles() {
    return roles;
  }

  public void setRoles(final List<String> roles) {
    this.roles = roles;
  }

  public String getUserPrincipalName() {
    return userPrincipalName;
  }

  public void setUserPrincipalName(final String userPrincipalName) {
    this.userPrincipalName = userPrincipalName;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final Member member = (Member) o;
    return Objects.equals(userId, member.userId)
        && Objects.equals(userPrincipalName, member.userPrincipalName)
        && Objects.equals(roles, member.roles);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, userPrincipalName, roles);
  }

  @Override
  public String toString() {
    return "Member{"
        + "userId='"
        + userId
        + "'"
        + ", userPrincipalName='"
        + userPrincipalName
        + "'"
        + ", roles="
        + roles
        + "}";
  }
}
