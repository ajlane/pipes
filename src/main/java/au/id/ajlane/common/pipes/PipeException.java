package au.id.ajlane.common.pipes;

public class PipeException extends Exception {

  public PipeException(final Throwable cause) {
    super(notNull(cause));
  }

  public PipeException(final String message, final Throwable cause) {
    super(message, notNull(cause));
  }

  private static Throwable notNull(final Throwable cause) {
    if (cause != null) {
      return cause;
    }
    throw new IllegalArgumentException("The cause cannot be null.");
  }

}
