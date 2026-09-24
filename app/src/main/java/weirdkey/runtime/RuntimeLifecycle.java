package weirdkey.runtime;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class RuntimeLifecycle {
    private final Map<LifecyclePhase, List<Runnable>> callbacks = new EnumMap<>(LifecyclePhase.class);
    private LifecyclePhase currentPhase;

    RuntimeLifecycle() {
        for (LifecyclePhase phase : LifecyclePhase.values()) {
            callbacks.put(phase, new ArrayList<>());
        }
    }

    synchronized void on(LifecyclePhase phase, Runnable callback) {
        callbacks.get(phase).add(callback);
    }

    synchronized void run(LifecyclePhase phase, Runnable action) {
        currentPhase = phase;
        try {
            action.run();
            List.copyOf(callbacks.get(phase)).forEach(Runnable::run);
        } finally {
            currentPhase = null;
        }
    }

    synchronized Optional<LifecyclePhase> currentPhase() {
        return Optional.ofNullable(currentPhase);
    }
}