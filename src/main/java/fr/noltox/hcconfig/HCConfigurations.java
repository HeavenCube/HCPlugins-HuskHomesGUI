package fr.noltox.hcconfig;

import de.exlll.configlib.ConfigLib;
import de.exlll.configlib.NameFormatters;
import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;
import net.kyori.adventure.text.Component;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Central access point for loading and saving ConfigLib configurations with HCPlugins conventions.
 */
public final class HCConfigurations {

    private static final String PATH_PARAM = "path";

    /**
     * Standard configuration properties pre-configured for HCPlugins (kebab-case, Adventure Component serializer).
     */
    public static final YamlConfigurationProperties DEFAULT_PROPERTIES = ConfigLib.BUKKIT_DEFAULT_PROPERTIES
            .toBuilder()
            .setNameFormatter(NameFormatters.LOWER_KEBAB_CASE)
            .addSerializer(Component.class, HCComponentSerializer.INSTANCE)
            .build();

    private HCConfigurations() {
    }

    /**
     * Loads a configuration without writing missing values to disk.
     *
     * @param path              the target file path
     * @param configurationType the configuration class
     * @param <T>               the configuration type
     * @return the loaded configuration
     */
    public static <T> T load(Path path, Class<T> configurationType) {
        return YamlConfigurations.load(Objects.requireNonNull(path, PATH_PARAM), configurationType, DEFAULT_PROPERTIES);
    }

    /**
     * Loads or creates/updates a configuration, saving default comments and missing fields.
     *
     * @param path              the target file path
     * @param configurationType the configuration class
     * @param <T>               the configuration type
     * @return the updated configuration
     */
    public static <T> T update(Path path, Class<T> configurationType) {
        return YamlConfigurations.update(Objects.requireNonNull(path, PATH_PARAM), configurationType, DEFAULT_PROPERTIES);
    }

    /**
     * Loads or creates/updates a configuration with custom properties.
     *
     * @param path              the target file path
     * @param configurationType the configuration class
     * @param customizer        customizer for property builder
     * @param <T>               the configuration type
     * @return the updated configuration
     */
    public static <T> T update(
            Path path,
            Class<T> configurationType,
            Consumer<YamlConfigurationProperties.Builder<?>> customizer
    ) {
        YamlConfigurationProperties.Builder<?> builder = DEFAULT_PROPERTIES.toBuilder();
        customizer.accept(builder);
        return YamlConfigurations.update(Objects.requireNonNull(path, PATH_PARAM), configurationType, builder.build());
    }

    /**
     * Saves a configuration to disk.
     *
     * @param path              the target file path
     * @param configurationType the configuration class
     * @param value             the instance to save
     * @param <T>               the configuration type
     */
    public static <T> void save(Path path, Class<T> configurationType, T value) {
        YamlConfigurations.save(Objects.requireNonNull(path, PATH_PARAM), configurationType, value, DEFAULT_PROPERTIES);
    }
}
