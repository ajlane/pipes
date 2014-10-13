package au.id.ajlane.common.pipes;

public interface PipeConsumer<Input> {
  public void accept(final Input input) throws PipeException;
}
