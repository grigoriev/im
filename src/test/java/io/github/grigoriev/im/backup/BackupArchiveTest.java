package io.github.grigoriev.im.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** The export on disk: entries one at a time, rows one by one, and the limits against a crafted ZIP. */
class BackupArchiveTest {

    private final JsonMapper json = JsonMapper.builder().build();
    @TempDir
    private Path folder;

    private Path zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
            }
        }
        return Files.write(Files.createTempFile(folder, "export", ".zip"), out.toByteArray());
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private void read(Path file, long maxBytes, long maxFileBytes, int maxEntries, Consumer<BackupArchive> work) {
        BackupArchive.read(file, json, maxBytes, maxFileBytes, maxEntries, work);
    }

    @Test
    void readsAValueTheRowsOneByOneAndAFile() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", utf8("{\"format\":1}"));
        entries.put("data/t.json", utf8("[{\"id\":\"1\"},{\"id\":\"2\",\"content\":{\"file\":\"f\"}}]"));
        entries.put("data/empty.json", utf8("[]"));
        entries.put("f", new byte[] {1, 2, 3});
        List<Map<String, Object>> rows = new ArrayList<>();
        read(zip(entries), 1000, 100, 4, archive -> {
            assertThat(archive.value("manifest.json", new TypeReference<Map<String, Object>>() { }))
                    .containsEntry("format", 1);
            archive.rows("data/t.json", rows::add);
            archive.rows("data/empty.json", row -> rows.add(Map.of()));
            assertThat(archive.file("f")).containsExactly(1, 2, 3);
        });
        assertThat(rows).extracting(row -> row.get("id")).containsExactly("1", "2");
    }

    @Test
    void namesWhatIsMissingOrBroken() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("data/cut.json", utf8("[{\"id\":\"1\"},{\"id\""));
        entries.put("data/text.json", utf8("[\"x\"]"));
        entries.put("data/none.json", new byte[0]);
        entries.put("data/word.json", utf8("kaputt"));
        entries.put("data/object.json", utf8("{\"id\":\"1\"}"));
        List<Map<String, Object>> rows = new ArrayList<>();
        BackupException own = new BackupException("vom Empfänger");
        read(zip(entries), 1000, 100, 5, archive -> {
            for (String broken : List.of("data/none.json", "data/word.json", "data/object.json")) {
                assertThatThrownBy(() -> archive.rows(broken, rows::add)).as(broken).isInstanceOf(BackupException.class)
                        .hasMessage("Die Datensicherung ist beschädigt: " + broken + ".");
            }
            assertThatThrownBy(() -> archive.value("manifest.json", new TypeReference<Map<String, Object>>() { }))
                    .isInstanceOf(BackupException.class)
                    .hasMessage("Diese Datei ist keine Datensicherung dieser App: manifest.json fehlt.");
            assertThatThrownBy(() -> archive.file("files/x")).isInstanceOf(BackupException.class)
                    .hasMessage("In der Datensicherung fehlt die Datei files/x.");
            assertThatThrownBy(() -> archive.rows("data/cut.json", rows::add)).isInstanceOf(BackupException.class)
                    .hasMessage("Die Datensicherung ist beschädigt: data/cut.json.");
            assertThat(rows).as("the rows before the break went on").hasSize(1);
            assertThatThrownBy(() -> archive.rows("data/text.json", rows::add)).isInstanceOf(BackupException.class)
                    .hasMessage("Die Datensicherung ist beschädigt: data/text.json.");
            assertThatThrownBy(() -> archive.rows("data/cut.json", row -> {
                throw own;
            })).as("a problem of the receiver passes unchanged").isSameAs(own);
        });
    }

    @Test
    void aBrokenCompressedFileIsADamagedExport() throws IOException {
        byte[] content = new byte[4000];
        Arrays.fill(content, (byte) 'a');
        Path file = zip(Map.of("f", content));
        byte[] bytes = Files.readAllBytes(file);
        // The data of the only entry starts after its local header of 30 bytes and its name.
        for (int i = 31; i < 37; i++) {
            bytes[i] = (byte) ~bytes[i];
        }
        Files.write(file, bytes);
        read(file, 10_000, 10_000, 1, archive -> assertThatThrownBy(() -> archive.file("f"))
                .isInstanceOf(BackupException.class).hasMessage("Die Datensicherung ist beschädigt: f."));
    }

    @Test
    void refusesNoZipAndTooManyEntries() throws IOException {
        Path text = Files.write(folder.resolve("text.zip"), utf8("kein zip"));
        List<BackupArchive> opened = new ArrayList<>();
        assertThatThrownBy(() -> read(text, 1000, 100, 2, opened::add)).isInstanceOf(BackupException.class)
                .hasMessage("Diese Datei ist kein ZIP.");
        Map<String, byte[]> two = new LinkedHashMap<>();
        two.put("a", new byte[1]);
        two.put("b", new byte[1]);
        Path file = zip(two);
        read(file, 1000, 100, 2, opened::add);
        assertThat(opened).hasSize(1);
        assertThatThrownBy(() -> read(file, 1000, 100, 1, opened::add)).isInstanceOf(BackupException.class)
                .hasMessage(BackupArchive.TOO_BIG);
        assertThat(opened).hasSize(1);
    }

    @Test
    void refusesAFileOrAnExportThatExpandsTooFar() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("a", new byte[10]);
        entries.put("b", new byte[10]);
        entries.put("data/rows.json", utf8("[{\"id\":\"1\"}]"));
        Path file = zip(entries);
        read(file, 1000, 10, 3, archive -> assertThat(archive.file("a")).as("a file of the limit").hasSize(10));
        List<Map<String, Object>> rows = new ArrayList<>();
        read(file, 1000, 9, 3, archive -> {
            assertThatThrownBy(() -> archive.file("a")).hasMessage(BackupArchive.TOO_BIG);
            archive.rows("data/rows.json", rows::add);
        });
        assertThat(rows).as("the rows count against the whole export only").hasSize(1);
        read(file, 20, 10, 3, archive -> {
            archive.file("a");
            archive.file("b");
            assertThatThrownBy(() -> archive.rows("data/rows.json", row -> { })).hasMessage(BackupArchive.TOO_BIG);
        });
    }

    @Test
    void countsSingleBytesUpToTheLimit() throws IOException {
        List<Long> counted = new ArrayList<>();
        try (InputStream in = new BackupArchive.Limited(new ByteArrayInputStream(new byte[] {7, 8}), 2, counted::add)) {
            assertThat(in.read()).isEqualTo(7);
            assertThat(in.read()).isEqualTo(8);
            assertThat(in.read()).as("the end counts nothing").isEqualTo(-1);
            assertThat(in.read(new byte[4], 0, 4)).isEqualTo(-1);
        }
        assertThat(counted).containsExactly(1L, 1L, 0L, 0L);
        try (InputStream in = new BackupArchive.Limited(new ByteArrayInputStream(new byte[] {7, 8}), 1, bytes -> { })) {
            assertThat(in.read()).isEqualTo(7);
            assertThatThrownBy(in::read).hasMessage(BackupArchive.TOO_BIG);
        }
    }
}
