package au.id.ajlane.common.pipes;

public interface PipeFunction<TInput, TOutput> {
  public TOutput apply(TInput input) throws PipeException;
}
