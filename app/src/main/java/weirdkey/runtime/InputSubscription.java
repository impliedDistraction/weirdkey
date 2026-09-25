package weirdkey.runtime;

public interface InputSubscription extends AutoCloseable {
    boolean isActive();

    void cancel();

    @Override
    default void close() {
        cancel();
    }
}