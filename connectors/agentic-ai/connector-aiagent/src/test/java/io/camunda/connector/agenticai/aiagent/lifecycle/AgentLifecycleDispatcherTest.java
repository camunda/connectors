/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentFlavor;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentJobKind;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentJobObserver;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentLifecycleListener;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.JobDeferred;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.JobStarted;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ToolDiscoveryDispatched;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.TurnCompleted;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.api.outbound.JobContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

class AgentLifecycleDispatcherTest {

  private static final String STATE_KEY = "lifecycle:stateful";

  private final JobContext jobContext = Mockito.mock(JobContext.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
  private final Logger lifecycleLogger =
      (Logger) LoggerFactory.getLogger("io.camunda.connector.agenticai.aiagent.lifecycle");
  private Level previousLogLevel;

  @BeforeEach
  void captureLogs() {
    previousLogLevel = lifecycleLogger.getLevel();
    lifecycleLogger.setLevel(Level.DEBUG);
    logs.start();
    lifecycleLogger.addAppender(logs);
  }

  @AfterEach
  void releaseLogs() {
    lifecycleLogger.detachAppender(logs);
    lifecycleLogger.setLevel(previousLogLevel);
  }

  @Test
  void withoutListenersTheLifecycleDoesNothing() {
    final var dispatcher = new AgentLifecycleDispatcher(List.of(), objectMapper, 1000);

    final var lifecycle = start(dispatcher, AgentContext.empty());

    assertThat(lifecycle).isSameAs(AgentJobLifecycle.NOOP);
  }

  @Test
  void listenersThatReturnNoObserverLeaveTheLifecycleDoingNothing() {
    final AgentLifecycleListener listener = () -> "idle";
    final var dispatcher = new AgentLifecycleDispatcher(List.of(listener), objectMapper, 1000);

    assertThat(start(dispatcher, AgentContext.empty())).isSameAs(AgentJobLifecycle.NOOP);
  }

  @Test
  void eventsAreNotBuiltWithoutListeners() {
    final var built = new AtomicInteger();
    final var lifecycle = AgentJobLifecycle.NOOP;

    lifecycle.jobDeferred(
        () -> {
          built.incrementAndGet();
          return new JobDeferred(List.of());
        });
    final var context =
        lifecycle.turnCompleted(
            AgentContext.empty(),
            () -> {
              built.incrementAndGet();
              return turnCompleted();
            });

    assertThat(built).hasValue(0);
    assertThat(context.properties()).isEmpty();
  }

  @Test
  void eventIsBuiltOnceForAllListeners() {
    final var built = new AtomicInteger();
    final var seen = new ArrayList<ToolDiscoveryDispatched>();
    final var dispatcher =
        new AgentLifecycleDispatcher(
            List.of(
                stateful(
                    "one",
                    e -> {
                      seen.add(e);
                      return null;
                    }),
                stateful(
                    "two",
                    e -> {
                      seen.add(e);
                      return null;
                    })),
            objectMapper,
            1000);

    start(dispatcher, AgentContext.empty())
        .toolDiscoveryDispatched(
            AgentContext.empty(),
            () -> {
              built.incrementAndGet();
              return new ToolDiscoveryDispatched(List.of());
            });

    assertThat(built).hasValue(1);
    assertThat(seen).hasSize(2);
  }

  @Test
  void failureWhileBuildingAnEventSkipsTheCallbackAndNeverReachesTheJob() {
    final var received = new ArrayList<JobDeferred>();
    final var listener =
        new AgentLifecycleListener() {
          @Override
          public String id() {
            return "listener";
          }

          @Override
          public AgentJobObserver onJobStarted(JobStarted event) {
            return new AgentJobObserver() {
              @Override
              public void onJobDeferred(JobDeferred event) {
                received.add(event);
              }
            };
          }
        };
    final var lifecycle =
        start(
            new AgentLifecycleDispatcher(List.of(listener), objectMapper, 1000),
            AgentContext.empty());

    lifecycle.jobDeferred(
        () -> {
          throw new IllegalStateException("secret prompt text");
        });
    lifecycle.jobDeferred(() -> new JobDeferred(List.of()));

    assertThat(received).hasSize(1);
    assertThat(warnings())
        .singleElement()
        .satisfies(
            message -> {
              assertThat(message).contains("job deferred").contains("IllegalStateException");
              assertThat(message).doesNotContain("secret prompt text");
            });
  }

  @Test
  void failureOfTheJobAnnouncementYieldsALifecycleThatDoesNothing() {
    Mockito.when(jobContext.getJobKey()).thenThrow(new IllegalStateException("broken"));
    final var dispatcher =
        new AgentLifecycleDispatcher(List.of(stateful("one", e -> null)), objectMapper, 1000);

    assertThat(start(dispatcher, AgentContext.empty())).isSameAs(AgentJobLifecycle.NOOP);
    assertThat(warnings()).singleElement().asString().contains("IllegalStateException");
  }

  @Test
  void throwingListenerDoesNotStopOthersAndStateLandsInItsOwnNamespace() {
    final var seen = new ArrayList<JobStarted>();
    final var throwing =
        new AgentLifecycleListener() {
          @Override
          public String id() {
            return "throwing";
          }

          @Override
          public AgentJobObserver onJobStarted(JobStarted event) {
            return new AgentJobObserver() {
              @Override
              public Map<String, Object> onToolDiscoveryDispatched(ToolDiscoveryDispatched e) {
                throw new IllegalStateException("boom");
              }
            };
          }
        };
    final var stateful =
        new AgentLifecycleListener() {
          @Override
          public String id() {
            return "stateful";
          }

          @Override
          public AgentJobObserver onJobStarted(JobStarted event) {
            seen.add(event);
            return new AgentJobObserver() {
              @Override
              public Map<String, Object> onToolDiscoveryDispatched(ToolDiscoveryDispatched e) {
                return Map.of("status", "OPEN");
              }
            };
          }
        };
    final var dispatcher =
        new AgentLifecycleDispatcher(List.of(throwing, stateful), objectMapper, 1000);
    final var previous = AgentContext.empty().withProperty(STATE_KEY, Map.of("a", 1));

    final var result =
        start(dispatcher, previous)
            .toolDiscoveryDispatched(previous, () -> new ToolDiscoveryDispatched(List.of()));

    assertThat(seen.getFirst().previousState()).containsEntry("a", 1);
    assertThat(result.properties())
        .containsOnlyKeys(STATE_KEY)
        .containsEntry(STATE_KEY, Map.of("status", "OPEN"));
  }

  @Test
  void listenerFailureIsLoggedWithoutItsMessageAndWithTheThrowableOnlyAtDebug() {
    final var listener =
        new AgentLifecycleListener() {
          @Override
          public String id() {
            return "leaky";
          }

          @Override
          public AgentJobObserver onJobStarted(JobStarted event) {
            return new AgentJobObserver() {
              @Override
              public void onJobDeferred(JobDeferred event) {
                throw new IllegalArgumentException("prompt: tell me a secret");
              }
            };
          }
        };
    final var lifecycle =
        start(
            new AgentLifecycleDispatcher(List.of(listener), objectMapper, 1000),
            AgentContext.empty());

    lifecycle.jobDeferred(() -> new JobDeferred(List.of()));

    final var warn = logs.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    assertThat(warn).hasSize(1);
    assertThat(warn.getFirst().getFormattedMessage())
        .contains("leaky")
        .contains("job deferred")
        .contains("java.lang.IllegalArgumentException")
        .doesNotContain("prompt: tell me a secret");
    assertThat(warn.getFirst().getThrowableProxy()).isNull();

    final var debug = logs.list.stream().filter(e -> e.getLevel() == Level.DEBUG).toList();
    assertThat(debug).hasSize(1);
    assertThat(debug.getFirst().getThrowableProxy().getMessage())
        .isEqualTo("prompt: tell me a secret");
  }

  @Test
  void validStateIsStoredAsACopyOfThePlainJsonTree() {
    final var nested = new LinkedHashMap<String, Object>();
    nested.put("flag", true);
    nested.put("nothing", null);
    nested.put("list", new ArrayList<>(List.of(1, 2.5, "three", Map.of("deep", List.of()))));
    final var state = new HashMap<String, Object>();
    state.put("nested", nested);
    state.put("text", "plain");
    state.put("count", 42L);
    final var dispatcher =
        new AgentLifecycleDispatcher(List.of(stateful("stateful", e -> state)), objectMapper, 4096);

    final var result = discover(dispatcher, AgentContext.empty());
    nested.put("changedLater", "after the contribution");

    final var expectedNested = new LinkedHashMap<String, Object>();
    expectedNested.put("flag", true);
    expectedNested.put("nothing", null);
    expectedNested.put("list", List.of(1, 2.5, "three", Map.of("deep", List.of())));
    final var expected = new HashMap<String, Object>();
    expected.put("nested", expectedNested);
    expected.put("text", "plain");
    expected.put("count", 42L);
    assertThat(result.properties()).containsEntry(STATE_KEY, expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidStates")
  void invalidStateIsDroppedAndThePreviousStateStays(
      String description, Map<String, Object> state, String expectedReason) {
    final var dispatcher =
        new AgentLifecycleDispatcher(List.of(stateful("stateful", e -> state)), objectMapper, 4096);
    final var previous = AgentContext.empty().withProperty(STATE_KEY, Map.of("kept", "value"));

    final var result = discover(dispatcher, previous);

    assertThat(result.properties()).containsEntry(STATE_KEY, Map.of("kept", "value"));
    assertThat(warnings())
        .singleElement()
        .satisfies(
            message -> {
              assertThat(message).contains("stateful").contains(expectedReason);
              assertThat(message).doesNotContain("leaked-state-content");
            });
  }

  static Stream<Arguments> invalidStates() {
    final var selfReferencing = new HashMap<String, Object>();
    selfReferencing.put("self", selfReferencing);
    final var tooDeep = new HashMap<String, Object>();
    var current = tooDeep;
    for (int i = 0; i < 100; i++) {
      final var next = new HashMap<String, Object>();
      current.put("n", next);
      current = next;
    }
    final var nonStringKey = new HashMap<Object, Object>();
    nonStringKey.put(1, "leaked-state-content");

    return Stream.of(
        Arguments.of("arbitrary object", Map.of("value", new Object()), "unsupported value type"),
        Arguments.of(
            "set",
            Map.of("value", new HashSet<>(List.of("leaked-state-content"))),
            "unsupported value type"),
        Arguments.of(
            "array",
            Map.of("value", new String[] {"leaked-state-content"}),
            "unsupported value type"),
        Arguments.of("date", Map.of("value", new java.util.Date()), "unsupported value type"),
        Arguments.of("record", Map.of("value", turnCompleted()), "unsupported value type"),
        Arguments.of("not a number", Map.of("value", Double.NaN), "unsupported value type"),
        Arguments.of(
            "infinity", Map.of("value", Float.POSITIVE_INFINITY), "unsupported value type"),
        Arguments.of(
            "non-string key in nested map",
            Map.of("value", nonStringKey),
            "unsupported value type"),
        Arguments.of(
            "non-string value inside a list",
            Map.of("value", List.of("leaked-state-content", new Object())),
            "unsupported value type"),
        Arguments.of("self-referencing map", selfReferencing, "nesting too deep"),
        Arguments.of("deeply nested maps", tooDeep, "nesting too deep"),
        Arguments.of(
            "oversized text",
            Map.of("value", "leaked-state-content".repeat(500)),
            "exceeds the limit"));
  }

  @Test
  void sizeLimitCountsUtf8BytesOfTheSerializedState() {
    // 60 characters, but 120 bytes in UTF-8
    final var multiByte = Map.<String, Object>of("v", "é".repeat(60));
    final var ascii = Map.<String, Object>of("v", "e".repeat(60));

    final var multiByteResult =
        discover(
            new AgentLifecycleDispatcher(
                List.of(stateful("stateful", e -> multiByte)), objectMapper, 100),
            AgentContext.empty());
    final var asciiResult =
        discover(
            new AgentLifecycleDispatcher(
                List.of(stateful("stateful", e -> ascii)), objectMapper, 100),
            AgentContext.empty());

    assertThat(multiByteResult.properties()).isEmpty();
    assertThat(asciiResult.properties()).containsEntry(STATE_KEY, ascii);
  }

  @Test
  void oversizedStateOfOneListenerDoesNotAffectTheOthers() {
    final var dispatcher =
        new AgentLifecycleDispatcher(
            List.of(
                stateful("big", e -> Map.of("payload", "x".repeat(100))),
                stateful("stateful", e -> Map.of("ok", true))),
            objectMapper,
            50);

    final var result = discover(dispatcher, AgentContext.empty());

    assertThat(result.properties()).containsOnlyKeys(STATE_KEY);
  }

  @Test
  void turnCompletedContributionIsValidatedLikeTheDiscoveryContribution() {
    final var listener =
        new AgentLifecycleListener() {
          @Override
          public String id() {
            return "stateful";
          }

          @Override
          public AgentJobObserver onJobStarted(JobStarted event) {
            return new AgentJobObserver() {
              @Override
              public Map<String, Object> onTurnCompleted(TurnCompleted event) {
                return Map.of("value", new Object());
              }
            };
          }
        };
    final var lifecycle =
        start(
            new AgentLifecycleDispatcher(List.of(listener), objectMapper, 4096),
            AgentContext.empty());

    final var result = lifecycle.turnCompleted(AgentContext.empty(), () -> turnCompleted());

    assertThat(result.properties()).isEmpty();
    assertThat(warnings()).singleElement().asString().contains("unsupported value type");
  }

  private static AgentLifecycleListener stateful(
      String id, Function<ToolDiscoveryDispatched, Map<String, Object>> contribution) {
    return new AgentLifecycleListener() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public AgentJobObserver onJobStarted(JobStarted event) {
        return new AgentJobObserver() {
          @Override
          public Map<String, Object> onToolDiscoveryDispatched(ToolDiscoveryDispatched e) {
            return contribution.apply(e);
          }
        };
      }
    };
  }

  private AgentContext discover(AgentLifecycleDispatcher dispatcher, AgentContext context) {
    return start(dispatcher, context)
        .toolDiscoveryDispatched(context, () -> new ToolDiscoveryDispatched(List.of()));
  }

  private AgentJobLifecycle start(AgentLifecycleDispatcher dispatcher, AgentContext context) {
    return dispatcher.startJob(
        AgentFlavor.SUB_PROCESS, AgentJobKind.TOOL_DISCOVERY, jobContext, context, Instant.now());
  }

  private List<String> warnings() {
    return logs.list.stream()
        .filter(e -> e.getLevel() == Level.WARN)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  private static TurnCompleted turnCompleted() {
    return new TurnCompleted(1, List.of(), null, AgentMetrics.empty());
  }
}
