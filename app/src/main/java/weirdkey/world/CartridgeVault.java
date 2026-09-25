package weirdkey.world;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import weirdkey.runtime.Cartridge;

public final class CartridgeVault {
    private final Path rootDirectory;

    public CartridgeVault(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public List<CartridgeManifest> discover() {
        if (!Files.isDirectory(rootDirectory)) {
            return List.of();
        }

        try (Stream<Path> entries = Files.list(rootDirectory)) {
            return entries
                .filter(Files::isDirectory)
                .map(directory -> directory.resolve("cartridge.md"))
                .filter(Files::isRegularFile)
                .map(this::readManifest)
                .sorted(
                    Comparator.comparing((CartridgeManifest manifest) -> manifest.availability() != CartridgeAvailability.AVAILABLE)
                        .thenComparing(CartridgeManifest::name)
                )
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to discover cartridges from " + rootDirectory, exception);
        }
    }

    public Cartridge instantiate(CartridgeManifest manifest) {
        if (manifest.availability() != CartridgeAvailability.AVAILABLE) {
            throw new IllegalStateException("Cartridge is not available: " + manifest.id());
        }

        String entryClassName = manifest.entry()
            .orElseThrow(() -> new IllegalStateException("Available cartridge is missing an entry: " + manifest.id()));
        try {
            Class<?> entryClass = Class.forName(entryClassName);
            if (!Cartridge.class.isAssignableFrom(entryClass)) {
                throw new IllegalStateException("Entry does not implement Cartridge: " + entryClassName);
            }
            return (Cartridge) entryClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException
            | InstantiationException
            | IllegalAccessException
            | InvocationTargetException
            | NoSuchMethodException exception) {
            throw new IllegalStateException("Failed to load cartridge entry " + entryClassName, exception);
        }
    }

    private CartridgeManifest readManifest(Path manifestPath) {
        List<String> lines;
        try {
            lines = Files.readAllLines(manifestPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read cartridge manifest " + manifestPath, exception);
        }

        if (lines.size() < 3 || !"---".equals(lines.get(0).trim())) {
            throw new IllegalStateException("Expected frontmatter in " + manifestPath);
        }

        int frontmatterEnd = lines.subList(1, lines.size()).indexOf("---");
        if (frontmatterEnd < 0) {
            throw new IllegalStateException("Unterminated frontmatter in " + manifestPath);
        }

        Map<String, String> metadata = parseFrontmatter(lines.subList(1, frontmatterEnd + 1), manifestPath);
        String description = String.join(System.lineSeparator(), lines.subList(frontmatterEnd + 2, lines.size())).trim();
        String id = required(metadata, "id", manifestPath);
        String name = required(metadata, "name", manifestPath);
        CartridgeAvailability availability = CartridgeAvailability.valueOf(
            required(metadata, "availability", manifestPath).trim().toUpperCase()
        );
        Optional<String> entry = Optional.ofNullable(metadata.get("entry"));
        if (availability == CartridgeAvailability.AVAILABLE && entry.isEmpty()) {
            throw new IllegalStateException("Available cartridge is missing entry metadata in " + manifestPath);
        }

        return new CartridgeManifest(id, name, availability, entry, description);
    }

    private static Map<String, String> parseFrontmatter(List<String> lines, Path manifestPath) {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            int separator = line.indexOf(':');
            if (separator < 0) {
                throw new IllegalStateException("Invalid frontmatter line in " + manifestPath + ": " + line);
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            metadata.put(key, value);
        }
        return metadata;
    }

    private static String required(Map<String, String> metadata, String key, Path manifestPath) {
        String value = metadata.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing " + key + " in " + manifestPath);
        }
        return value;
    }
}
