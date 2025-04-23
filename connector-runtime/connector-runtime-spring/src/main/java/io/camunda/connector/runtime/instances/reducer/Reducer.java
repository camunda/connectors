package io.camunda.connector.runtime.instances.reducer;

/**
 * A functional interface that defines a method for merging two objects of the same type.
 * <b>Note:</b> Remember to register your reducer in the {@link ReducerRegistry} to make it
 * available for use.
 *
 * @param <T> the type of objects to be merged
 */
public interface Reducer<T> {
  T reduce(T a, T b);
}
