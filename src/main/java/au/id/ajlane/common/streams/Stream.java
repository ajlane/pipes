package au.id.ajlane.common.streams;

import java.io.Closeable;

public interface Stream<T> extends Closeable {
    @Override
    public void close() throws StreamCloseException;

    public boolean hasNext() throws StreamReadException;

    public T next() throws StreamReadException;
}
