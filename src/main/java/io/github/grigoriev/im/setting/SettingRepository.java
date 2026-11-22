package io.github.grigoriev.im.setting;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads and writes the single values of table {@code app_setting}, one row per {@link SettingKey}. */
@Repository
public class SettingRepository {

    static final String READ = "SELECT value FROM app_setting WHERE key = :key";
    static final String WRITE = """
            INSERT INTO app_setting (key, value) VALUES (:key, :value)
            ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value""";
    static final String RESET = "DELETE FROM app_setting WHERE key = :key";

    private final JdbcClient jdbc;

    /**
     * Creates the repository.
     *
     * @param jdbc the database
     */
    public SettingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Reads a value.
     *
     * @param key the key
     * @return the stored value, empty if there is none
     */
    public Optional<String> read(SettingKey key) {
        return jdbc.sql(READ).param("key", key.stored()).query(String.class).optional();
    }

    /**
     * Writes a value, in place of the one before.
     *
     * @param key the key
     * @param value the value; {@code null} keeps the row without a value
     */
    public void write(SettingKey key, String value) {
        jdbc.sql(WRITE).param("key", key.stored()).param("value", value).update();
    }

    /**
     * Removes a value, so the built-in one applies again.
     *
     * @param key the key
     */
    public void reset(SettingKey key) {
        jdbc.sql(RESET).param("key", key.stored()).update();
    }
}
