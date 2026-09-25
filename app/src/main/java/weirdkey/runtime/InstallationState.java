package weirdkey.runtime;

@FunctionalInterface
public interface InstallationState {
    void install(InstallationContext context);
}
