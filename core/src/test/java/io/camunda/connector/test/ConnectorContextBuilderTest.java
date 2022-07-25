package io.camunda.connector.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;

import java.util.HashSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ConnectorContextBuilderTest {

  @Test
  public void shouldProvideVariablesAsObject() {

    // given
    var obj = new Object();

    // when
    var context = ConnectorContextBuilder.create().variables(obj).build();

    // then
    assertThat(context.getVariablesAsType(Object.class)).isEqualTo(obj);
  }

  @Test
  public void shouldProvideVariablesAsObject_failIfNotProvided() {

    // given
    var context = ConnectorContextBuilder.create().build();

    // when
    var exception =
        catchException(
            () -> {
              context.getVariablesAsType(Object.class);
            });

    // then
    assertThat(exception).hasMessage("variablesAsObject not provided");
  }

  @Test
  public void shouldProvideVariablesAsObject_failIfIncompatible() {

    // given
    var set = new HashSet<String>();

    var context = ConnectorContextBuilder.create().variables(set).build();

    // when
    var exception =
        catchException(
            () -> {
              context.getVariablesAsType(Map.class);
            });

    // then
    assertThat(exception).hasMessage("no variablesAsObject of type java.util.Map provided");
  }

  @Test
  public void shouldProvideVariablesAsString() {

    // given
    var json = "{ \"foo\" : \"FOO\" }";

    // when
    var context = ConnectorContextBuilder.create().variables(json).build();

    // then
    assertThat(context.getVariables()).isEqualTo(json);
  }

  @Test
  public void shouldProvideVariablesAsString_failIfNotProvided() {

    // given
    var context = ConnectorContextBuilder.create().build();

    // when
    var exception =
        catchException(
            () -> {
              context.getVariables();
            });

    // then
    assertThat(exception).hasMessage("variablesAsJSON not provided");
  }

  @Test
  public void shouldThrowOnConflictingVariableDefinitions_jsonVariablesAlreadySet() {

    // when
    var exception =
        catchException(
            () -> {
              ConnectorContextBuilder.create().variables("{ }").variables(new Object());
            });

    // then
    assertThat(exception).hasMessage("variablesAsJSON already set");
  }

  @Test
  public void shouldThrowOnConflictingVariableDefinitions_objectVariablesAlreadySet() {

    // when
    var exception =
        catchException(
            () -> {
              ConnectorContextBuilder.create().variables(new Object()).variables("{ }");
            });

    // then
    assertThat(exception).hasMessage("variablesAsObject already set");
  }

  @Test
  public void shouldThrowOnDuplicateVariableDefinition() {

    // when
    var exception =
        catchException(
            () -> {
              ConnectorContextBuilder.create().variables("{ }").variables("{ }");
            });

    // then
    assertThat(exception).hasMessage("variablesAsJSON already set");
  }

  @Test
  public void shouldProvideSecret() {

    // given
    var context = ConnectorContextBuilder.create().secret("foo", "FOO").build();

    // when
    var replaced = context.getSecretStore().replaceSecret("secrets.foo");

    // then
    assertThat(replaced).isEqualTo("FOO");
  }

  @Test
  public void shouldProvideSecret_failIfNotProvided() {

    // given
    var context = ConnectorContextBuilder.create().build();

    // when
    var exception =
        catchException(
            () -> {
              context.getSecretStore().replaceSecret("secrets.foo");
            });

    // then
    assertThat(exception).hasMessage("Secret with name 'foo' is not available");
  }
}
