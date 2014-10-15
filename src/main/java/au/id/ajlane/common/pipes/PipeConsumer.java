package au.id.ajlane.common.pipes;

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public interface PipeConsumer<TInput> {

  public static <TInput> PipeConsumer<TInput> combine(
      final Iterable<? extends PipeConsumer<? super TInput>> consumers
  ) {
    return new PipeConsumer<TInput>() {
      @Override
      public void accept(final TInput input) throws PipeException {
        for (final PipeConsumer<? super TInput> consumer : consumers) {
          consumer.accept(input);
        }
      }

      @Override
      public void flush() throws PipeException {
        for (final PipeConsumer<? super TInput> consumer : consumers) {
          consumer.flush();
        }
      }
    };
  }

  public void accept(final TInput input) throws PipeException;

  public default PipeConsumer<TInput> async(
      final Consumer<PipeException> exceptionConsumer) {
    return async(exceptionConsumer, ForkJoinPool.commonPool());
  }

  public default PipeConsumer<TInput> async(
      final Consumer<PipeException> exceptionConsumer,
      final Executor executor) {
    final PipeConsumer<TInput> innerConsumer = this;
    return new PipeConsumer<TInput>() {
      @Override
      public void accept(final TInput input) {
        executor.execute(() -> {
          try {
            innerConsumer.accept(input);
          } catch (final PipeException ex) {
            exceptionConsumer.accept(ex);
          }
        });
      }

      @Override
      public void flush() {
        executor.execute(() -> {
          try {
            innerConsumer.flush();
          } catch (final PipeException ex) {
            exceptionConsumer.accept(ex);
          }
        });
      }
    };
  }

  public default void flush() throws PipeException {
    // Do nothing by default
  }

  public default void flush(final TInput input) throws PipeException {
    accept(input);
    flush();
  }

  public default PipeConsumer<TInput> redirectErrors(final Consumer<PipeException> errorConsumer) {
    final PipeConsumer<TInput> original = this;
    return new PipeConsumer<TInput>() {
      @Override
      public void accept(final TInput input) {
        try {
          original.accept(input);
        } catch (final PipeException e) {
          errorConsumer.accept(e);
        }
      }

      @Override
      public void flush() {
        try {
          original.flush();
        } catch (final PipeException e) {
          errorConsumer.accept(e);
        }
      }
    };
  }

  public default <TNewInput> PipeConsumer<TNewInput> transform(
      final PipeFunction<? super TNewInput, ? extends TInput> function) {
    final PipeConsumer<TInput> original = this;
    return new PipeConsumer<TNewInput>() {
      @Override
      public void accept(final TNewInput input) throws PipeException {
        original.accept(function.apply(input));
      }

      @Override
      public void flush() throws PipeException {
        original.flush();
      }
    };
  }
}
