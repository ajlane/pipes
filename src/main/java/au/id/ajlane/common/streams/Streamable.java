package au.id.ajlane.common.streams;

public interface Streamable<T> {
    public Stream<? extends T> stream();
}
