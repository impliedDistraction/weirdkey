package weirdkey.runtime.events;

public interface EventSubscription extends AutoCloseable {
    boolean isActive();

    void cancel();

    @Override
    default void close() {
        cancel();
    }
}