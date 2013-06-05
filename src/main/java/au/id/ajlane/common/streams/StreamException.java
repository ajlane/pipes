package au.id.ajlane.common.streams;

import java.io.IOException;

public abstract class StreamException extends IOException {
    public StreamException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
