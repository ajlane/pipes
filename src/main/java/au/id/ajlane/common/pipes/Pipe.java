package au.id.ajlane.common.pipes;

import au.id.ajlane.common.UnhandledCheckedException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface Pipe<TInput, TOutput> extends PipeConsumer<TInput> {

  public default void accept(final TInput input) throws PipeException {
    accept(input, i -> {
    });
  }

  public void accept(final TInput input, final PipeConsumer<TOutput> output) throws PipeException;

  public default <TNewOutput> Pipe<TInput, TNewOutput> then(final Pipe<TOutput, TNewOutput> pipe) {
    return (input, output) -> this.accept(input, intermediate -> pipe.accept(intermediate, output));
  }

  public default <TNewOutput> Pipe<TInput, TNewOutput> thenAsync(
      final Pipe<TOutput, TNewOutput> pipe,
      final Consumer<PipeException> exceptionConsumer) {
    return then(pipe.toAsync(exceptionConsumer));
  }

  public default <TNewOutput> Pipe<TInput, TNewOutput> thenAsync(
      final Pipe<TOutput, TNewOutput> pipe,
      final Consumer<PipeException> exceptionConsumer,
      final Executor executor) {
    return then(pipe.toAsync(exceptionConsumer, executor));
  }

  public default <TNewOutput> Pipe<TInput, TNewOutput> fork(
      final Pipe<TOutput, TNewOutput>... pipes) {
    return fork(Arrays.asList(pipes));
  }

  public default <TNewOutput> Pipe<TInput, TNewOutput> fork(
      final Collection<Pipe<TOutput, TNewOutput>> pipes) {
    return fork(pipes.stream());
  }

  public default <TNewOutput> Pipe<TInput, TNewOutput> fork(
      final Stream<? extends Pipe<TOutput, TNewOutput>> pipes) {
    return (input, output) -> pipes.forEach(this::then);
  }

  public default <TNewOutput> Pipe<TInput, TNewOutput> forkAsync(
      final Collection<Pipe<TOutput, TNewOutput>> pipes,
      final Consumer<PipeException> exceptionConsumer,
      final Executor executor) {
    return forkAsync(pipes.stream(), exceptionConsumer, executor);
  }

  public default <TNewOutput> Pipe<TInput, TNewOutput> forkAsync(
      final Stream<? extends Pipe<TOutput, TNewOutput>> pipes,
      final Consumer<PipeException> exceptionConsumer,
      final Executor executor) {
    return fork(pipes.<Pipe<TOutput, TNewOutput>>map(
        pipe -> pipe.toAsync(exceptionConsumer, executor)));
  }

  public default <TAccumulate, TResult> TResult collect(final TInput input,
                                                        final Collector<? super TOutput, TAccumulate, TResult> collector)
      throws PipeException {
    final TAccumulate accumulate = collector.supplier().get();
    accept(input, output -> collector.accumulator().accept(accumulate, output));
    return collector.finisher().apply(accumulate);
  }

  public default Pipe<TInput, TOutput> filter(final PipePredicate<? super TInput> predicate) {
    return (input, output) -> {
      if (predicate.apply(input)) {
        accept(input, output);
      }
    };
  }

  public default List<TOutput> toList(final TInput input) throws PipeException {
    return collect(input, Collectors.toList());
  }

  public default Set<TOutput> toSet(final TInput input) throws PipeException {
    return collect(input, Collectors.toSet());
  }

  public default Pipe<TInput, TOutput> toAsync(
      final Consumer<PipeException> exceptionConsumer) {
    return toAsync(exceptionConsumer, ForkJoinPool.commonPool());
  }

  public default Pipe<TInput, TOutput> toAsync(
      final Consumer<PipeException> exceptionConsumer,
      final Executor executor) {
    return (input, output) ->
        CompletableFuture.runAsync(() -> {
          try {
            accept(input, output);
          } catch (final PipeException ex) {
            throw new UnhandledCheckedException(ex);
          }
        }, executor).exceptionally(ex -> {
          if (ex.getClass().equals(UnhandledCheckedException.class)) {
            final Throwable cause = ex.getCause();
            if (cause instanceof PipeException) {
              exceptionConsumer.accept((PipeException) cause);
            }
          }
          if (ex instanceof RuntimeException) {
            throw (RuntimeException) ex;
          } else if (ex instanceof Error) {
            throw (Error) ex;
          } else {
            throw new UnhandledCheckedException((Exception) ex);
          }
        });
  }

  public default <TNewOutput> Pipe<TInput, TNewOutput> map(
      final PipeFunction<TOutput, TNewOutput> function) {
    return (input, output) -> accept(
        input,
        intermediate -> output.accept(function.apply(intermediate))
    );
  }
}
