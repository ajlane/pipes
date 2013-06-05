package au.id.ajlane.common.streams;

public interface StreamTransform<T, R> {
    public R apply(T item) throws StreamTransformException;

    public void close() throws StreamCloseException;
}
