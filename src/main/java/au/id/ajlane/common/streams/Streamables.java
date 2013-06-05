package au.id.ajlane.common.streams;

public abstract class Streamables {

    private static final Streamable<?> EMPTY = new Streamable<Object>() {
        @Override
        public Stream<?> stream() {
            return Streams.empty();
        }
    };

    @SuppressWarnings("unchecked")
    public static <T> Streamable<T> empty() {
        return (Streamable<T>) EMPTY;
    }

    public static <T> Streamable<T> singleton(final T item) {
        return new Streamable<T>() {
            @Override
            public Stream<T> stream() {
                return Streams.singleton(item);
            }
        };
    }

    public static <T> Streamable<T> fromIterable(final Iterable<T> iterable) {
        return new Streamable<T>() {
            @Override
            public Stream<T> stream() {
                return Streams.fromIterator(iterable.iterator());
            }
        };
    }

    public static <T> Streamable<T> concat(final Stream<? extends Streamable<T>> streamables) {
        return new Streamable<T>() {
            @Override
            public Stream<T> stream() {
                return new AbstractStream<T>() {

                    private Stream<? extends T> current = null;

                    @Override
                    protected void open() throws StreamReadException {
                        if (streamables.hasNext()) {
                            current = streamables.next().stream();
                        }
                    }

                    @Override
                    protected T find() throws StreamReadException {
                        while (current != null) {
                            if (current.hasNext()) {
                                return current.next();
                            } else {
                                try {
                                    current.close();
                                } catch (StreamCloseException ex) {
                                    throw new StreamReadException("Could not close one of the concatenated streams.", ex);
                                }
                                if (streamables.hasNext()) {
                                    current = streamables.next().stream();
                                } else {
                                    current = null;
                                }
                            }
                        }
                        return terminate();
                    }

                    @Override
                    protected void end() throws StreamCloseException {
                        if (current != null) current.close();
                    }
                };
            }
        };
    }

    public static <T> Streamable<T> concat(final Streamable<T>... streamables) {
        return new Streamable<T>() {
            @Override
            public Stream<T> stream() {
                return new AbstractStream<T>() {

                    private int i = 0;
                    private Stream<? extends T> current = null;

                    @Override
                    protected void open() throws StreamReadException {
                        if (i < streamables.length) {
                            current = streamables[i].stream();
                        }
                    }

                    @Override
                    protected T find() throws StreamReadException {
                        while (current != null) {
                            if (current.hasNext()) {
                                return current.next();
                            } else {
                                try {
                                    current.close();
                                } catch (StreamCloseException ex) {
                                    throw new StreamReadException("Could not close one of the concatenated streams.", ex);
                                }
                                if (i < streamables.length) {
                                    current = streamables[i].stream();
                                } else {
                                    current = null;
                                }
                            }
                        }
                        return terminate();
                    }

                    @Override
                    protected void end() throws StreamCloseException {
                        if (current != null) current.close();
                    }
                };
            }
        };
    }

    private Streamables() throws InstantiationException {
        throw new InstantiationException();
    }
}
