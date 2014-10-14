package au.id.ajlane.common.pipes;

public interface PipeConsumer<Input> {

  public void accept(final Input input) throws PipeException;

  public default void flush() throws PipeException {
    // Do nothing by default
  }

  public default void flush(final Input input) throws PipeException {
    accept(input);
    flush();
  }
}
