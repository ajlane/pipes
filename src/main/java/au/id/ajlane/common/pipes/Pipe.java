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

public interface Pipe<TInput, TOutput> {

  public void accept(final TInput input, final PipeConsumer<TOutput> output) throws PipeException;

  public default void acceptEach(final Iterable<TInput> inputs, final PipeConsumer<TOutput> output)
      throws PipeException {
    for (final TInput input : inputs) {
      accept(input, output);
    }
  }

  public default void flush(final TInput input, final PipeConsumer<TOutput> output)
      throws PipeException {
    accept(input, output);
    flush(output);
  }

  public default void flushAll(final Iterable<TInput> inputs,
                               final PipeConsumer<TOutput> output)
      throws PipeException {
    acceptEach(inputs, output);
    flush(output);
  }

  public default void flushEach(final Iterable<TInput> inputs,
                                final PipeConsumer<TOutput> output)
      throws PipeException {
    for (final TInput input : inputs) {
      flush(input, output);
    }
  }

  public default void flush(final PipeConsumer<TOutput> output) throws PipeException {
    output.flush();
  }

  public default <TNewOutput> Pipe<TInput, TNewOutput> then(final Pipe<TOutput, TNewOutput> pipe) {
    return (input, output) -> this.accept(input, new PipeConsumer<TOutput>() {
      @Override
      public void accept(final TOutput intermediate) throws PipeException {
        pipe.accept(intermediate, output);
      }

      @Override
      public void flush() throws PipeException {
        pipe.flush(output);
      }
    });
  }

  public default <TNewOutput> Pipe<TInput, TNewOutput> thenAsync(
      final Pipe<TOutput, TNewOutput> pipe,
      final Consumer<PipeException> exceptionConsumer) {
    return then(pipe.async(exceptionConsumer));
  }

  public default <TNewOutput> Pipe<TInput, TNewOutput> thenAsync(
      final Pipe<TOutput, TNewOutput> pipe,
      final Consumer<PipeException> exceptionConsumer,
      final Executor executor) {
    return then(pipe.async(exceptionConsumer, executor));
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
        pipe -> pipe.async(exceptionConsumer, executor)));
  }

  public default <TAccumulate, TResult> TResult collect(final TInput input,
                                                        final Collector<? super TOutput, TAccumulate, TResult> collector)
      throws PipeException {
    final TAccumulate accumulate = collector.supplier().get();
    flush(input, output -> collector.accumulator().accept(accumulate, output));
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

  public default PipeConsumer<TInput> asPipeConsumer() {
    return new PipeConsumer<TInput>() {
      @Override
      public void accept(final TInput input) throws PipeException {
        Pipe.this.accept(input, i -> {
        });
      }

      @Override
      public void flush() throws PipeException {
        Pipe.this.flush(i -> {
        });
      }
    };
  }

  public default Pipe<TInput, TOutput> async(
      final Consumer<PipeException> exceptionConsumer) {
    return async(exceptionConsumer, ForkJoinPool.commonPool());
  }

  public default Pipe<TInput, TOutput> async(
      final Consumer<PipeException> exceptionConsumer,
      final Executor executor) {
    final Pipe<TInput, TOutput> innerPipe = this;
    return new Pipe<TInput, TOutput>() {
      @Override
      public void accept(final TInput input, final PipeConsumer<TOutput> output) {
        run(() -> {
          try {
            innerPipe.accept(input, output);
          } catch (final PipeException ex) {
            throw new UnhandledCheckedException(ex);
          }
        });
      }

      @Override
      public void flush(final PipeConsumer<TOutput> output) {
        run(() -> {
          try {
            innerPipe.flush(output);
          } catch (final PipeException ex) {
            throw new UnhandledCheckedException(ex);
          }
        });
      }

      private void run(final Runnable task) {
        CompletableFuture.runAsync(task, executor).exceptionally(ex -> {
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
    };
  }

  public default <TNewOutput> Pipe<TInput, TNewOutput> map(
      final PipeFunction<TOutput, TNewOutput> function) {
    return (input, output) -> accept(
        input,
        intermediate -> output.accept(function.apply(intermediate))
    );
  }
}
