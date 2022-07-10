package io.camunda.connector.sdk;

import static org.assertj.core.api.Assertions.*;

import io.camunda.connector.sdk.test.ConnectorContextBuilder;
import org.junit.Test;

public class ExampleFunctionTest {

  @Test
  public void shouldExecuteConnector() throws Exception {

    // given
    var fn = new ExampleFunction();

    // when
    var context = ConnectorContextBuilder.create().variables(new ExampleInput("FOO")).build();

    var result = fn.execute(context);

    // then
    assertThat(result).isEqualTo("FOO");
  }

  @Test
  public void shouldReplaceSecret() throws Exception {

    // given
    var fn = new ExampleFunction();

    // when
    var context =
        ConnectorContextBuilder.create()
            .variables(new ExampleInput("secrets.FOO"))
            .secret("FOO", "SECRET_FOO")
            .build();

    var result = fn.execute(context);

    // then
    assertThat(result).isEqualTo("SECRET_FOO");
  }

  @Test
  public void shouldValidateInput() {

    // given
    var fn = new ExampleFunction();

    var context = ConnectorContextBuilder.create().variables(new ExampleInput()).build();

    // when
    var exception =
        catchException(
            () -> {
              fn.execute(context);
            });

    // then
    assertThat(exception).hasMessage("Property 'Test - foo' is missing");
  }

  @Test
  public void shouldFail() {

    // given
    var fn = new ExampleFunction();

    // when
    var context = ConnectorContextBuilder.create().variables(new ExampleInput("BOOM!")).build();

    var exception =
        catchException(
            () -> {
              fn.execute(context);
            });

    // then
    assertThat(exception).hasMessage("expected BOOM!");
  }
}
