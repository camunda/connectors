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
package io.camunda.connector.runtime.test;

import io.camunda.zeebe.client.api.ZeebeFuture;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectorTestZeebeFutureImpl implements ZeebeFuture {
  private static final Logger LOG = LoggerFactory.getLogger(ConnectorTestZeebeFutureImpl.class);

  @Override
  public Object join() {
    LOG.debug("FakeZeebeFuture join called");
    return null;
  }

  @Override
  public Object join(long timeout, TimeUnit unit) {
    return null;
  }

  @Override
  public CompletionStage thenApply(Function fn) {
    return null;
  }

  @Override
  public CompletionStage thenApplyAsync(Function fn) {
    return null;
  }

  @Override
  public CompletionStage thenApplyAsync(Function fn, Executor executor) {
    return null;
  }

  @Override
  public CompletionStage<Void> thenAccept(Consumer action) {
    return null;
  }

  @Override
  public CompletionStage<Void> thenAcceptAsync(Consumer action) {
    return null;
  }

  @Override
  public CompletionStage<Void> thenAcceptAsync(Consumer action, Executor executor) {
    return null;
  }

  @Override
  public CompletionStage<Void> thenRun(Runnable action) {
    return null;
  }

  @Override
  public CompletionStage<Void> thenRunAsync(Runnable action) {
    return null;
  }

  @Override
  public CompletionStage<Void> thenRunAsync(Runnable action, Executor executor) {
    return null;
  }

  @Override
  public CompletionStage<Void> acceptEither(CompletionStage other, Consumer action) {
    return null;
  }

  @Override
  public CompletionStage<Void> acceptEitherAsync(CompletionStage other, Consumer action) {
    return null;
  }

  @Override
  public CompletionStage<Void> acceptEitherAsync(
      CompletionStage other, Consumer action, Executor executor) {
    return null;
  }

  @Override
  public CompletableFuture toCompletableFuture() {
    return null;
  }

  @Override
  public CompletionStage exceptionally(Function fn) {
    return null;
  }

  @Override
  public CompletionStage whenCompleteAsync(BiConsumer action, Executor executor) {
    return null;
  }

  @Override
  public CompletionStage whenCompleteAsync(BiConsumer action) {
    return null;
  }

  @Override
  public CompletionStage whenComplete(BiConsumer action) {
    return null;
  }

  @Override
  public CompletionStage handleAsync(BiFunction fn, Executor executor) {
    return null;
  }

  @Override
  public CompletionStage handleAsync(BiFunction fn) {
    return null;
  }

  @Override
  public CompletionStage handle(BiFunction fn) {
    return null;
  }

  @Override
  public CompletionStage thenComposeAsync(Function fn, Executor executor) {
    return null;
  }

  @Override
  public CompletionStage thenComposeAsync(Function fn) {
    return null;
  }

  @Override
  public CompletionStage thenCompose(Function fn) {
    return null;
  }

  @Override
  public CompletionStage<Void> runAfterEitherAsync(
      CompletionStage other, Runnable action, Executor executor) {
    return null;
  }

  @Override
  public CompletionStage<Void> runAfterEitherAsync(CompletionStage other, Runnable action) {
    return null;
  }

  @Override
  public CompletionStage<Void> runAfterEither(CompletionStage other, Runnable action) {
    return null;
  }

  @Override
  public CompletionStage applyToEitherAsync(CompletionStage other, Function fn, Executor executor) {
    return null;
  }

  @Override
  public CompletionStage applyToEitherAsync(CompletionStage other, Function fn) {
    return null;
  }

  @Override
  public CompletionStage applyToEither(CompletionStage other, Function fn) {
    return null;
  }

  @Override
  public CompletionStage<Void> runAfterBothAsync(
      CompletionStage other, Runnable action, Executor executor) {
    return null;
  }

  @Override
  public CompletionStage<Void> runAfterBothAsync(CompletionStage other, Runnable action) {
    return null;
  }

  @Override
  public CompletionStage<Void> runAfterBoth(CompletionStage other, Runnable action) {
    return null;
  }

  @Override
  public CompletionStage<Void> thenAcceptBothAsync(
      CompletionStage other, BiConsumer action, Executor executor) {
    return null;
  }

  @Override
  public CompletionStage<Void> thenAcceptBothAsync(CompletionStage other, BiConsumer action) {
    return null;
  }

  @Override
  public CompletionStage<Void> thenAcceptBoth(CompletionStage other, BiConsumer action) {
    return null;
  }

  @Override
  public CompletionStage thenCombineAsync(CompletionStage other, BiFunction fn, Executor executor) {
    return null;
  }

  @Override
  public CompletionStage thenCombineAsync(CompletionStage other, BiFunction fn) {
    return null;
  }

  @Override
  public CompletionStage thenCombine(CompletionStage other, BiFunction fn) {
    return null;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return false;
  }

  @Override
  public boolean isCancelled() {
    return false;
  }

  @Override
  public boolean isDone() {
    return false;
  }

  @Override
  public Object get() throws InterruptedException, ExecutionException {
    return null;
  }

  @Override
  public Object get(long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return null;
  }
}
