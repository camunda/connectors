package io.camunda.connector.runtime.outbound.jobhandling;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * {@link ScheduledExecutorService} implementation, adopted to virtual threads.
 * Unlike {@link java.util.concurrent.ScheduledThreadPoolExecutor ScheduledThreadPoolExecutor},
 * threads are not pooled and reused, but created per task by the means of
 * {@link java.util.concurrent.ThreadPerTaskExecutor ThreadPerTaskExecutor}.
 * Threads are removed from the thread list upon their completion.
 *
 * <p/> While {@link #ThreadPerTaskScheduledExecutorService(ExecutorService)} constructor accepts arbitrary
 * implementation of {@link ExecutorService}, only non-pooling, thread-per-task implementations
 * will make sense because otherwise
 * {@link java.util.concurrent.ScheduledThreadPoolExecutor ScheduledThreadPoolExecutor} could be used instead.
 *
 * <p> <strong>Implementation details:</strong>
 * <ul>
 * <li>task submission is delegated to {@link java.util.concurrent.ThreadPerTaskExecutor ThreadPerTaskExecutor}
 * implementation of {@link ExecutorService};</li>
 * <li>scheduling is organized by the means of stock {@link DelayQueue};</li>
 * <li>{@link Runnable} task is wrapped into a specialized subclass of {@link FutureTask}
 * which implements {@link Delayed} interface;
 * almost all functionality, related to completion, exception/failure, cancellation etc
 * is delegated to the base class; upon submission an instance of this class is placed into the queue;</li>
 * <li>special dedicated (daemon) thread {@link ThreadPerTaskScheduledExecutorService.QueueReader QueueReader}
 * takes the task instances from the queue
 * and submits them to inner {@link ExecutorService}; this thread gets killed upon Service's shutdown or closure;</li>
 * <li> periodic tasks re-enqueue themselves upon completion, {@link FutureTask#runAndReset} is used
 * to execute them instead of {@link FutureTask#run}.
 * </ul>
 *
 */
public class ThreadPerTaskScheduledExecutorService implements ScheduledExecutorService {

  private final ExecutorService executorService;
  private final DelayQueue<DelayedTask<?>> queue = new DelayQueue<>();
  private final QueueReader queueReader;
  private final AtomicBoolean shutdown = new AtomicBoolean();

  public ThreadPerTaskScheduledExecutorService() {
    this(Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("virtual-", 0).factory()));
  }

  public ThreadPerTaskScheduledExecutorService(ExecutorService executorService) {
    this.executorService = executorService;
    queueReader = new QueueReader();
    queueReader.start();
  }

  // ExecutorService

  public void execute(Runnable command) {
    executorService.execute(command);
  }

  public void shutdown() {
    shutdownQueue();
    executorService.shutdown();
  }

  public List<Runnable> shutdownNow() {
    shutdownQueue();
    return executorService.shutdownNow();
  }

  private void shutdownQueue() {
    if (shutdown.compareAndSet(false, true)) {
      DelayedTask<?> task = null;
      while ((task = queue.peek()) != null) {
        queue.remove(task);
        task.cancel(true);
      }
      queueReader.kill();
    }
  }

  public boolean isShutdown() {
    return executorService.isShutdown();
  }

  public boolean isTerminated() {
    return executorService.isTerminated();
  }

  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return executorService.awaitTermination(timeout, unit);
  }

  public <T> Future<T> submit(Callable<T> task) {
    return executorService.submit(task);
  }

  public <T> Future<T> submit(Runnable task, T result) {
    return executorService.submit(task, result);
  }

  public Future<?> submit(Runnable task) {
    return executorService.submit(task);
  }

  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
    return executorService.invokeAll(tasks);
  }

  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException {
    return executorService.invokeAll(tasks, timeout, unit);
  }

  public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
    return executorService.invokeAny(tasks);
  }

  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return executorService.invokeAny(tasks, timeout, unit);
  }

  public void close() {
    ScheduledExecutorService.super.close();
    executorService.close();
  }

  // ScheduledExecutorService

  private static class DelayedTask<V> extends FutureTask<V> implements ScheduledFuture<V> {

    public static final DelayedTask<Void> POISON_PILL = new DelayedTask<>();

    public enum PeriodicMode {
      RATE,
      DELAY,
      NONE
    }

    private final long period;
    private final PeriodicMode periodicMode;
    private final Consumer<DelayedTask<V>> enqueuer;
    private volatile long triggerTime;

    private DelayedTask() {
      super(() -> {return null;});
      period = -1;
      periodicMode = null;
      enqueuer = null;
    }

    public DelayedTask(Callable<V> callable, long triggerTime, Consumer<DelayedTask<V>> enqueuer) {
      this(callable, triggerTime, -1, PeriodicMode.NONE, enqueuer);
    }

    public DelayedTask(Callable<V> callable, long triggerTime, long period, PeriodicMode periodicMode, Consumer<DelayedTask<V>> enqueuer) {
      super(callable);
      this.period = period;
      this.periodicMode = periodicMode;
      this.triggerTime = triggerTime;
      this.enqueuer = enqueuer;
      enqueuer.accept(this);
    }

    @Override
    public int compareTo(Delayed o) {
      return o instanceof DelayedTask other ? (int)(triggerTime - other.triggerTime) : 1;
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return unit.convert(triggerTime - System.nanoTime(), NANOSECONDS);
    }

    @Override
    public void run() {
      switch (periodicMode) {
        case NONE -> {
          super.run();
        }
        case DELAY -> {
          runAndReset();
          triggerTime = System.nanoTime() + period;
          enqueuer.accept(this);
        }
        case RATE -> {
          final long nextRun = System.nanoTime() + period;
          runAndReset();
          triggerTime = Math.max(nextRun, System.nanoTime());
          enqueuer.accept(this);
        }
        default -> {
          throw new Error("Unexpected " + PeriodicMode.class.getSimpleName() + " value " + periodicMode);
        }
      }
    }

  }

  private class QueueReader extends Thread {

    public QueueReader() {
      setDaemon(true);
      setName(ThreadPerTaskScheduledExecutorService.class.getSimpleName() + " "
          + getClass().getSimpleName());
    }

    @Override
    public void run() {
      while (true) {
        try {
          final DelayedTask<?> task = queue.take();
          if (task == DelayedTask.POISON_PILL)
            break;
          else {
            if (!shutdown.get())
              submit(task);
            else
              task.cancel(true);
          }
        } catch (InterruptedException e) {
        }
      }
    }

    public void kill() {
      queue.add(DelayedTask.POISON_PILL);
      try {
        join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

  }

  @Override
  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    return schedule(() -> {
      command.run();
      return null;
    }, delay, unit);
  }

  @Override
  public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
    return new DelayedTask<>(callable, triggerTime(delay, unit), this::enqueueTask);
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
    return new DelayedTask<>(() -> {
      command.run();
      return null;
    }, triggerTime(initialDelay, unit), unit.toNanos(period), DelayedTask.PeriodicMode.RATE, this::enqueueTask);
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
    return new DelayedTask<>(() -> {
      command.run();
      return null;
    }, triggerTime(initialDelay, unit), unit.toNanos(delay), DelayedTask.PeriodicMode.DELAY, this::enqueueTask);
  }

  private void enqueueTask(DelayedTask<?> task) {
    if (shutdown.get())
      throw new RejectedExecutionException("Service is shut down");
    queue.add(task);

  }

  // Adopted from ScheduledThreadPoolExecutor

  /**
   * Returns the nanoTime-based trigger time of a delayed action.
   */
  private long triggerTime(long delay, TimeUnit unit) {
    return triggerTime(unit.toNanos((delay < 0) ? 0 : delay));
  }
  /**
   * Returns the nanoTime-based trigger time of a delayed action.
   */
  private long triggerTime(long delay) {
    return System.nanoTime() +
        ((delay < (Long.MAX_VALUE >> 1)) ? delay : overflowFree(delay));
  }

  /**
   * Constrains the values of all delays in the queue to be within
   * Long.MAX_VALUE of each other, to avoid overflow in compareTo.
   * This may occur if a task is eligible to be dequeued, but has
   * not yet been, while some other task is added with a delay of
   * Long.MAX_VALUE.
   */
  private long overflowFree(long delay) {
    Delayed head = (Delayed) queue.peek();
    if (head != null) {
      long headDelay = head.getDelay(NANOSECONDS);
      if (headDelay < 0 && (delay - headDelay < 0))
        delay = Long.MAX_VALUE + headDelay;
    }
    return delay;
  }

}