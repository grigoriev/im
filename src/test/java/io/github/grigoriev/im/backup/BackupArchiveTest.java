package io.github.grigoriev.im.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.CRC32;
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
        BackupArchive.read(file, json, maxBytes, maxFileBytes, maxEntries, 10_000, work);
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
        entries.put("data/twice.json", utf8("[{\"id\":\"1\"}][{\"id\":\"2\"}]"));
        List<Map<String, Object>> rows = new ArrayList<>();
        BackupException own = new BackupException("vom Empfänger");
        read(zip(entries), 1000, 100, 6, archive -> {
            for (String broken : List.of("data/none.json", "data/word.json", "data/object.json", "data/twice.json")) {
                assertThatThrownBy(() -> archive.rows(broken, row -> { })).as(broken).isInstanceOf(BackupException.class)
                        .hasMessage("Die Datensicherung ist beschädigt: " + broken + ".");
            }
            TypeReference<Map<String, Object>> manifest = new TypeReference<>() { };
            assertThatThrownBy(() -> archive.value("manifest.json", manifest))
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
        read(file, 10_000, 10_000, 1, archive -> assertThatThrownBy(() -> archive.rows("f", row -> { }))
                .as("while the parser starts").isInstanceOf(BackupException.class)
                .hasMessage("Die Datensicherung ist beschädigt: f."));
    }

    @Test
    void aChangedByteOfAStoredFileIsADamagedExport() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] content = utf8("unverändert");
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            ZipEntry entry = new ZipEntry("f");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(content.length);
            CRC32 crc = new CRC32();
            crc.update(content);
            entry.setCrc(crc.getValue());
            zip.putNextEntry(entry);
            zip.write(content);
            entry = new ZipEntry("data/rows.json");
            zip.putNextEntry(entry);
            zip.write(utf8("[{\"id\":\"1\"}]  "));
        }
        byte[] bytes = out.toByteArray();
        Path fine = Files.write(folder.resolve("fine.zip"), bytes);
        List<Map<String, Object>> rows = new ArrayList<>();
        read(fine, 1000, 100, 2, archive -> {
            assertThat(archive.file("f")).isEqualTo(content);
            archive.rows("data/rows.json", rows::add);
        });
        assertThat(rows).as("the rest after the array counts into the CRC, not as a row").hasSize(1);
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        bytes[text.indexOf("unver")] = 'U';
        Path changed = Files.write(folder.resolve("changed.zip"), bytes);
        read(changed, 1000, 100, 2, archive -> assertThatThrownBy(() -> archive.file("f"))
                .isInstanceOf(BackupException.class).hasMessage("Die Datensicherung ist beschädigt: f."));
    }

    @Test
    void refusesACentralDirectoryTooBigOrTooLongBeforeOpeningIt() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < 3; i++) {
                ZipEntry entry = new ZipEntry("e" + i);
                entry.setComment("x".repeat(100));
                zip.putNextEntry(entry);
            }
            zip.setComment("a comment of the archive, which the end record carries");
        }
        Path file = Files.write(folder.resolve("comments.zip"), out.toByteArray());
        List<BackupArchive> opened = new ArrayList<>();
        BackupArchive.read(file, json, 1000, 100, 3, 1000, opened::add);
        assertThat(opened).as("within both limits, with an archive comment").hasSize(1);
        assertThatThrownBy(() -> BackupArchive.read(file, json, 1000, 100, 3, 300, opened::add))
                .hasMessage(BackupArchive.TOO_BIG);
        assertThatThrownBy(() -> BackupArchive.read(file, json, 1000, 100, 2, 1000, opened::add))
                .as("the count of the end record").hasMessage(BackupArchive.TOO_BIG);
        assertThat(opened).hasSize(1);
        byte[] bytes = out.toByteArray();
        int end = new String(bytes, StandardCharsets.ISO_8859_1).lastIndexOf("PK\u0005\u0006");
        long size = ByteBuffer.wrap(bytes, end + 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        BackupArchive.read(file, json, 1000, 100, 3, size, opened::add);
        assertThat(opened).as("a directory of exactly the limit").hasSize(2);
        assertThatThrownBy(() -> BackupArchive.read(file, json, 1000, 100, 3, size - 1, opened::add))
                .hasMessage(BackupArchive.TOO_BIG);

        // An end record alone, at the very start of the file, that claims five entries.
        ByteBuffer endRecord = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN).putInt(0x06054b50).putShort((short) 0)
                .putShort((short) 0).putShort((short) 5).putShort((short) 5);
        Path claimed = Files.write(folder.resolve("claimed.zip"), endRecord.array());
        assertThatThrownBy(() -> BackupArchive.read(claimed, json, 1000, 100, 3, 1000, opened::add))
                .hasMessage(BackupArchive.TOO_BIG);

        // More than the longest comment before the end record: the check reads the tail of the file only.
        ByteArrayOutputStream large = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(large)) {
            zip.setLevel(0);
            zip.putNextEntry(new ZipEntry("big"));
            zip.write(new byte[70_000]);
            zip.putNextEntry(new ZipEntry("small"));
        }
        Path big = Files.write(folder.resolve("big.zip"), large.toByteArray());
        assertThatThrownBy(() -> BackupArchive.read(big, json, 100_000, 100_000, 2, 10, opened::add))
                .as("the directory size of the end record at the end of a large file").hasMessage(BackupArchive.TOO_BIG);
        // A fake end record with small counts in the archive comment hides no real one behind it.
        ByteBuffer fake = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN).putInt(0x06054b50).putShort((short) 0)
                .putShort((short) 0).putShort((short) 1).putShort((short) 1);
        ByteArrayOutputStream hidden = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(hidden)) {
            for (int i = 0; i < 3; i++) {
                zip.putNextEntry(new ZipEntry("e" + i));
            }
            zip.setComment(new String(fake.array(), StandardCharsets.ISO_8859_1));
        }
        Path faked = Files.write(folder.resolve("faked.zip"), hidden.toByteArray());
        assertThatThrownBy(() -> BackupArchive.read(faked, json, 1000, 100, 2, 1000, opened::add))
                .as("the real record behind a fake one").hasMessage(BackupArchive.TOO_BIG);
        Path tiny = Files.write(folder.resolve("tiny.zip"), utf8("PK"));
        assertThatThrownBy(() -> BackupArchive.read(tiny, json, 1000, 100, 3, 1000, opened::add))
                .hasMessage("Diese Datei ist kein ZIP.");
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
