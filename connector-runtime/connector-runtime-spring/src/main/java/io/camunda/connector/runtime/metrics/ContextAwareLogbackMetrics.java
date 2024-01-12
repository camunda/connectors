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
package io.camunda.connector.runtime.metrics;

import static java.util.Collections.emptyList;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

/** Copy of LogbackMetrics with the addition of logger name as a tag. */
@NonNullApi
public class ContextAwareLogbackMetrics extends LogbackMetrics {

  static ThreadLocal<Boolean> ignoreMetrics = new ThreadLocal<>();
  private final Iterable<Tag> tags;
  private final LoggerContext loggerContext;
  private final Map<MeterRegistry, MetricsTurboFilter> metricsTurboFilters = new HashMap<>();

  static {
    // see gh-2868. Without this called statically, the same call in the constructor
    // may return SubstituteLoggerFactory and fail to cast.
    LoggerFactory.getILoggerFactory();
  }

  public ContextAwareLogbackMetrics() {
    this(emptyList());
  }

  public ContextAwareLogbackMetrics(Iterable<Tag> tags) {
    this(tags, (LoggerContext) LoggerFactory.getILoggerFactory());
  }

  public ContextAwareLogbackMetrics(Iterable<Tag> tags, LoggerContext context) {
    this.tags = tags;
    this.loggerContext = context;

    loggerContext.addListener(
        new LoggerContextListener() {
          @Override
          public boolean isResetResistant() {
            return true;
          }

          @Override
          public void onReset(LoggerContext context) {
            // re-add turbo filter because reset clears the turbo filter list
            synchronized (metricsTurboFilters) {
              for (MetricsTurboFilter metricsTurboFilter : metricsTurboFilters.values()) {
                loggerContext.addTurboFilter(metricsTurboFilter);
              }
            }
          }

          @Override
          public void onStart(LoggerContext context) {
            // no-op
          }

          @Override
          public void onStop(LoggerContext context) {
            // no-op
          }

          @Override
          public void onLevelChange(Logger logger, Level level) {
            // no-op
          }
        });
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    MetricsTurboFilter filter = new MetricsTurboFilter(registry, tags);
    synchronized (metricsTurboFilters) {
      metricsTurboFilters.put(registry, filter);
      loggerContext.addTurboFilter(filter);
    }
  }

  public static void ignoreMetrics(Runnable runnable) {
    ignoreMetrics.set(true);
    try {
      runnable.run();
    } finally {
      ignoreMetrics.remove();
    }
  }

  @Override
  public void close() {
    synchronized (metricsTurboFilters) {
      for (MetricsTurboFilter metricsTurboFilter : metricsTurboFilters.values()) {
        loggerContext.getTurboFilterList().remove(metricsTurboFilter);
      }
    }
  }

  /** Copy of LogbackMetrics.MetricsTurboFilter with the addition of logger name as a tag. */
  @NonNullApi
  @NonNullFields
  static class MetricsTurboFilter extends TurboFilter {

    private static final String METER_NAME = "logback.events";
    private static final String METER_DESCRIPTION =
        "Number of log events that were enabled by the effective log level";

    private final Counter.Builder errorCounterTemplate;
    private final Counter.Builder warnCounterTemplate;
    private final Counter.Builder infoCounterTemplate;
    private final Counter.Builder debugCounterTemplate;
    private final Counter.Builder traceCounterTemplate;

    private final MeterRegistry registry;

    private record Counters(
        Counter errorCounter,
        Counter warnCounter,
        Counter infoCounter,
        Counter debugCounter,
        Counter traceCounter) {}

    private final Map<String, Counters> countersByLoggerName = new ConcurrentHashMap<>();

    MetricsTurboFilter(MeterRegistry registry, Iterable<Tag> tags) {
      this.registry = registry;

      errorCounterTemplate =
          Counter.builder(METER_NAME)
              .tags(tags)
              .tags("level", "error")
              .description(METER_DESCRIPTION)
              .baseUnit(BaseUnits.EVENTS);
      warnCounterTemplate =
          Counter.builder(METER_NAME)
              .tags(tags)
              .tags("level", "warn")
              .description(METER_DESCRIPTION)
              .baseUnit(BaseUnits.EVENTS);
      infoCounterTemplate =
          Counter.builder(METER_NAME)
              .tags(tags)
              .tags("level", "info")
              .description(METER_DESCRIPTION)
              .baseUnit(BaseUnits.EVENTS);
      debugCounterTemplate =
          Counter.builder(METER_NAME)
              .tags(tags)
              .tags("level", "debug")
              .description(METER_DESCRIPTION)
              .baseUnit(BaseUnits.EVENTS);

      traceCounterTemplate =
          Counter.builder(METER_NAME)
              .tags(tags)
              .tags("level", "trace")
              .description(METER_DESCRIPTION)
              .baseUnit(BaseUnits.EVENTS);
    }

    @Override
    public FilterReply decide(
        Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
      // When filter is asked for decision for an isDebugEnabled call or similar test,
      // there is no message (ie format)
      // and no intention to log anything with this call. We will not increment counters
      // and can return immediately and
      // avoid the relatively expensive ThreadLocal access below. See also logbacks
      // Logger.callTurboFilters().
      // Calling logger.isEnabledFor(level) might be sub-optimal since it calls this
      // filter again. This behavior caused a StackOverflowError in the past.
      if (format == null || !level.isGreaterOrEqual(logger.getEffectiveLevel())) {
        return FilterReply.NEUTRAL;
      }

      Boolean ignored = ContextAwareLogbackMetrics.ignoreMetrics.get();
      if (ignored != null && ignored) {
        return FilterReply.NEUTRAL;
      }

      ContextAwareLogbackMetrics.ignoreMetrics(() -> recordMetrics(level, logger));

      return FilterReply.NEUTRAL;
    }

    private void recordMetrics(Level level, Logger logger) {
      var loggerName = logger.getName();
      var counters = countersByLoggerName.computeIfAbsent(loggerName, this::createCounters);
      switch (level.toInt()) {
        case Level.ERROR_INT:
          counters.errorCounter.increment();
          break;
        case Level.WARN_INT:
          counters.warnCounter.increment();
          break;
        case Level.INFO_INT:
          counters.infoCounter.increment();
          break;
        case Level.DEBUG_INT:
          counters.debugCounter.increment();
          break;
        case Level.TRACE_INT:
          counters.traceCounter.increment();
          break;
      }
    }

    private Counters createCounters(String loggerName) {
      return new Counters(
          errorCounterTemplate.tags("logger", loggerName).register(registry),
          warnCounterTemplate.tags("logger", loggerName).register(registry),
          infoCounterTemplate.tags("logger", loggerName).register(registry),
          debugCounterTemplate.tags("logger", loggerName).register(registry),
          traceCounterTemplate.tags("logger", loggerName).register(registry));
    }
  }
}
