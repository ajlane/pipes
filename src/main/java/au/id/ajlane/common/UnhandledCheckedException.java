package au.id.ajlane.common;

public final class UnhandledCheckedException extends RuntimeException {

  private static final long serialVersionUID = 2910642114168597280L;

  public UnhandledCheckedException(final Exception cause) {
    super(notNull(cause));
  }

  private static Exception notNull(final Exception ex) {
    if (ex != null) {
      return ex;
    }
    throw new IllegalArgumentException("The cause cannot be null.");
  }
}
