package au.id.ajlane.common.streams;

public abstract class AbstractStreamTransform<T, R> implements StreamTransform<T, R> {

    private boolean isOpen = false;

    @Override
    public final R apply(T item) throws StreamTransformException {
        if (!isOpen) open();
        return transform(item);
    }

    @Override
    public void close() throws StreamCloseException {
    }

    protected abstract R transform(T item) throws StreamTransformException;

    private void open() throws StreamTransformException {

    }
}
