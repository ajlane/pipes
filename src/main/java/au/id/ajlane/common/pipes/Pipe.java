package au.id.ajlane.common.pipes;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A component in the data processing pipeline.
 * <p>
 * To use a {@code Pipe}, call {@link #accept(Object, PipeConsumer)} for each input, then call
 * {@link #flush(PipeConsumer)} to flush out any buffered results.
 * </p>
 * <p>
 * {@code Pipe}s can be chained together using {@link #append(Pipe)} or {@link #append(Iterable)}),
 * or adapted to run asynchronously using {@link #async(Consumer)}.
 * </p>
 * <p>
 * Most inheritors will only need to implement {@link #accept(Object, PipeConsumer)}.
 * </p>
 * <p>
 * Some inheritors (in particular, those which buffer their results) may also wish to implement
 * {@link #flush(PipeConsumer)}. If they do so, they must ensure that they call {@link
 * PipeConsumer#flush()} on the output consumer.
 * </p>
 */
public interface Pipe<TInput, TOutput> {

  public void accept(final TInput input, final PipeConsumer<? super TOutput> output)
      throws PipeException;

  public default void acceptEach(
      final Iterable<? extends TInput> inputs, final PipeConsumer<? super TOutput> output
  )
      throws PipeException {
    for (final TInput input : inputs) {
      accept(input, output);
    }
  }

  public default <TNewInput, TNewOutput> Pipe<TNewInput, TNewOutput> adapt(
      final PipeFunction<TNewInput, TInput> inputMap,
      final PipeFunction<TOutput, TNewOutput> outputMap
  ) {
    return (input, output) -> accept(
        inputMap.apply(input),
        intermediate -> output.accept(outputMap.apply(intermediate))
    );
  }

  public default <TNewOutput> Pipe<TInput, TNewOutput> append(
      final Pipe<? super TOutput, TNewOutput> pipe
  ) {
    return (input, output) -> this.accept(input, pipe.asPipeConsumer(output));
  }

  public default <TNewOutput> Pipe<TInput, TNewOutput> append(
      final Iterable<? extends Pipe<? super TOutput, TNewOutput>> pipes
  ) {
    return (input, output) -> {
      final Iterable<? extends PipeConsumer<? super TOutput>> consumers =
          StreamSupport.stream(pipes.spliterator(), false).
              <PipeConsumer<? super TOutput>>map(p -> p.asPipeConsumer(output))::iterator;
      this.accept(input, PipeConsumer.combine(consumers));
    };
  }

  public default <TNewOutput> Pipe<TInput, TNewOutput> appendParallel(
      final Iterable<? extends Pipe<? super TOutput, TNewOutput>> pipes,
      final Consumer<PipeException> exceptionConsumer
  ) {
    return appendParallel(pipes, exceptionConsumer, ForkJoinPool.commonPool());
  }

  public default <TNewOutput> Pipe<TInput, TNewOutput> appendParallel(
      final Iterable<? extends Pipe<? super TOutput, TNewOutput>> pipes,
      final Consumer<PipeException> exceptionConsumer,
      final Executor executor
  ) {
    return (input, output) -> {
      final Iterable<? extends PipeConsumer<? super TOutput>> consumers =
          StreamSupport.stream(pipes.spliterator(), false).
              <PipeConsumer<? super TOutput>>map(
                  p -> p.async(exceptionConsumer, executor).asPipeConsumer(output)
              )::iterator;
      this.accept(input, PipeConsumer.combine(consumers));
    };
  }

  public default PipeConsumer<TInput> asPipeConsumer() {
    return asPipeConsumer(o -> {
    });
  }

  public default PipeConsumer<TInput> asPipeConsumer(final PipeConsumer<? super TOutput> output) {
    return new PipeConsumer<TInput>() {
      @Override
      public void accept(final TInput input) throws PipeException {
        Pipe.this.accept(input, output);
      }

      @Override
      public void flush() throws PipeException {
        Pipe.this.flush(output);
      }
    };
  }

  public default Pipe<TInput, TOutput> async(
      final Consumer<PipeException> exceptionConsumer
  ) {
    return async(exceptionConsumer, ForkJoinPool.commonPool());
  }

  public default Pipe<TInput, TOutput> async(
      final Consumer<PipeException> exceptionConsumer,
      final Executor executor
  ) {
    final Pipe<TInput, TOutput> original = this;
    return new Pipe<TInput, TOutput>() {
      @Override
      public void accept(final TInput input, final PipeConsumer<? super TOutput> output) {
        executor.execute(() -> {
          try {
            original.accept(input, output);
          } catch (final PipeException ex) {
            exceptionConsumer.accept(ex);
          }
        });
      }

      @Override
      public void flush(final PipeConsumer<? super TOutput> output) {
        executor.execute(() -> {
          try {
            original.flush(output);
          } catch (final PipeException ex) {
            exceptionConsumer.accept(ex);
          }
        });
      }
    };
  }

  public default <TAccumulate, TResult> TResult collect(
      final TInput input,
      final Collector<? super TOutput, TAccumulate, TResult> collector
  )
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

  public default void flush(final TInput input, final PipeConsumer<? super TOutput> output)
      throws PipeException {
    accept(input, output);
    flush(output);
  }

  public default void flush(final PipeConsumer<? super TOutput> output) throws PipeException {
    output.flush();
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

  public default <TNewOutput> Pipe<TInput, TNewOutput> map(
      final PipeFunction<? super TOutput, TNewOutput> function) {
    return (input, output) -> accept(
        input,
        intermediate -> output.accept(function.apply(intermediate))
    );
  }

  public default Pipe<TInput, TOutput> redirectErrors(
      final Consumer<PipeException> exceptionConsumer) {
    final Pipe<TInput, TOutput> original = this;
    return new Pipe<TInput, TOutput>() {
      @Override
      public void accept(final TInput input, final PipeConsumer<? super TOutput> output) {
        try {
          original.accept(input, output);
        } catch (final PipeException ex) {
          exceptionConsumer.accept(ex);
        }
      }

      @Override
      public void flush(final TInput input, final PipeConsumer<? super TOutput> output) {
        try {
          original.flush(output);
        } catch (final PipeException ex) {
          exceptionConsumer.accept(ex);
        }
      }
    };
  }

  public default List<TOutput> toList(final TInput input) throws PipeException {
    return collect(input, Collectors.toList());
  }

  public default Set<TOutput> toSet(final TInput input) throws PipeException {
    return collect(input, Collectors.toSet());
  }
}
