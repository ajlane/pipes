package au.id.ajlane.common.pipes;

public interface PipePredicate<TInput> {

  public boolean apply(final TInput input) throws PipeException;
}
