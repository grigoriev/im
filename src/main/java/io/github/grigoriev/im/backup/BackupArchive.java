package io.github.grigoriev.im.backup;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

/**
 * An export on disk, read one entry at a time. The rows of a table stream one by one, and a file comes into memory
 * only while its row goes into the database. Limits keep a crafted ZIP from filling the memory or the disk.
 */
final class BackupArchive {

    static final String TOO_BIG = "Die Datensicherung ist zu groß oder hat zu viele Dateien.";

    private static final TypeReference<Map<String, Object>> ROW = new TypeReference<>() { };

    /** The end record of a ZIP: its signature, its size without the comment, and the longest comment. */
    private static final int END_SIGNATURE = 0x06054b50;
    private static final int END_RECORD = 22;
    private static final int MAX_COMMENT = 0xFFFF;

    private final ZipFile zip;
    private final JsonMapper json;
    private final long maxBytes;
    private final long maxFileBytes;
    private long expanded;

    private BackupArchive(ZipFile zip, JsonMapper json, long maxBytes, long maxFileBytes) {
        this.zip = zip;
        this.json = json;
        this.maxBytes = maxBytes;
        this.maxFileBytes = maxFileBytes;
    }

    /**
     * Opens an export, hands it to {@code work} and closes it again.
     *
     * @param file the ZIP
     * @param json reads the manifest and the rows
     * @param maxBytes the most all entries expand to together
     * @param maxFileBytes the most one entry that comes into memory expands to
     * @param maxEntries the most entries, below 65,535: a ZIP64 directory is refused
     * @param maxDirectoryBytes the most bytes of the central directory, which {@link ZipFile} reads into memory
     * @param work reads the export
     * @throws BackupException if the file is no ZIP or has too many entries, or from {@code work}
     */
    @SuppressWarnings("java:S107") // the limits of an import
    static void read(Path file, JsonMapper json, long maxBytes, long maxFileBytes, int maxEntries,
            long maxDirectoryBytes, Consumer<BackupArchive> work) {
        try {
            directory(file, maxEntries, maxDirectoryBytes);
            // The end record named the count of entries that ZipFile reads.
            try (ZipFile zip = new ZipFile(file.toFile())) {
                work.accept(new BackupArchive(zip, json, maxBytes, maxFileBytes));
            }
        } catch (IOException _) {
            throw new BackupException("Diese Datei ist kein ZIP.");
        }
    }

    /**
     * Checks the end records of the ZIP before {@link ZipFile} reads the whole central directory into memory: a crafted
     * directory, for example with long comments, could fill it first. {@link ZipFile} takes one of the end records in
     * the tail, not always the last: a fake one may hide in the archive comment. So every record there must keep the
     * limits. Without one the file is no ZIP, which {@link ZipFile} then says. A ZIP64 directory marks its counts with
     * all ones, which exceed both limits.
     */
    private static void directory(Path file, int maxEntries, long maxDirectoryBytes) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
            int tail = (int) Math.min(in.length(), (long) END_RECORD + MAX_COMMENT);
            byte[] bytes = new byte[tail];
            in.seek(in.length() - tail);
            in.readFully(bytes);
            ByteBuffer end = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            for (int at = tail - END_RECORD; at >= 0; at--) {
                if (end.getInt(at) == END_SIGNATURE && (Short.toUnsignedInt(end.getShort(at + 10)) > maxEntries
                        || Integer.toUnsignedLong(end.getInt(at + 12)) > maxDirectoryBytes)) {
                    throw new BackupException(TOO_BIG);
                }
            }
        }
    }

    /**
     * Reads a whole JSON entry, such as the manifest.
     *
     * @param name the entry
     * @param type the type of its content
     * @param <T> the type of its content
     * @return the content
     * @throws BackupException if the entry is missing, too big or no valid JSON
     */
    <T> T value(String name, TypeReference<T> type) {
        return entry(name, maxFileBytes, in -> {
            try {
                return json.readValue(in, type);
            } catch (JacksonException _) {
                throw damaged(name);
            }
        });
    }

    /**
     * Streams the rows of a JSON array, one at a time.
     *
     * @param name the entry
     * @param each takes each row
     * @throws BackupException if the entry is missing or no valid JSON array of rows
     */
    void rows(String name, Consumer<Map<String, Object>> each) {
        // Each row is one value of the array: the rows after it are no trailing garbage.
        ObjectReader reader = json.readerFor(ROW).without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        entry(name, maxBytes, in -> {
            try (JsonParser parser = json.createParser(in)) {
                if (token(name, parser) != JsonToken.START_ARRAY) {
                    throw damaged(name);
                }
                for (Optional<Map<String, Object>> row = next(name, parser, reader); row.isPresent();
                        row = next(name, parser, reader)) {
                    each.accept(row.get());
                }
                // A second array after the first would go unread: nothing may follow.
                if (token(name, parser) != null) {
                    throw damaged(name);
                }
            }
            return null;
        });
    }

    /**
     * The next row of the array, or none after the last. Anything else, an empty or cut entry too, is a damaged
     * export; a parse error is the fault of the file, not of the receiver of the rows.
     */
    private static Optional<Map<String, Object>> next(String name, JsonParser parser, ObjectReader reader) {
        JsonToken token = token(name, parser);
        if (token == JsonToken.END_ARRAY) {
            return Optional.empty();
        }
        if (token != JsonToken.START_OBJECT) {
            throw damaged(name);
        }
        try {
            return Optional.of(reader.readValue(parser));
        } catch (JacksonException _) {
            throw damaged(name);
        }
    }

    private static JsonToken token(String name, JsonParser parser) {
        try {
            return parser.nextToken();
        } catch (JacksonException _) {
            throw damaged(name);
        }
    }

    /**
     * Reads a file of a binary column.
     *
     * @param name the entry
     * @return its bytes
     * @throws BackupException if the entry is missing, too big or broken
     */
    byte[] file(String name) {
        if (zip.getEntry(name) == null) {
            throw new BackupException("In der Datensicherung fehlt die Datei " + name + ".");
        }
        return entry(name, maxFileBytes, InputStream::readAllBytes);
    }

    /** Reads an entry, at most {@code maxEntryBytes} of it; broken compressed data is a damaged export. */
    private <T> T entry(String name, long maxEntryBytes, EntryReader<T> reader) {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            throw new BackupException("Diese Datei ist keine Datensicherung dieser App: " + name + " fehlt.");
        }
        // ZipFile checks no CRC: the reader does, over the whole entry, so a changed byte is a damaged export too.
        CRC32 crc = new CRC32();
        try (InputStream in = new Limited(new CheckedInputStream(zip.getInputStream(entry), crc), maxEntryBytes,
                this::count)) {
            T value = reader.read(new KeepOpen(in));
            in.transferTo(OutputStream.nullOutputStream());
            if (crc.getValue() != entry.getCrc()) {
                throw damaged(name);
            }
            return value;
        } catch (IOException | JacksonException _) {
            // A broken start of the data fails while the parser detects the encoding, wrapped by Jackson.
            throw damaged(name);
        }
    }

    /** Hands an entry to a reader that closes what it reads, as Jackson does, and keeps it open for the CRC. */
    private static final class KeepOpen extends FilterInputStream {

        KeepOpen(InputStream in) {
            super(in);
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return in.read(buffer, offset, length);
        }

        @Override
        public void close() {
            // The entry closes after its CRC is checked.
        }
    }

    /** Reads the content of an entry. */
    @FunctionalInterface
    private interface EntryReader<T> {
        T read(InputStream in) throws IOException;
    }

    private static BackupException damaged(String name) {
        return new BackupException("Die Datensicherung ist beschädigt: " + name + ".");
    }

    private void count(long bytes) {
        expanded += bytes;
        if (expanded > maxBytes) {
            throw new BackupException(TOO_BIG);
        }
    }

    /** Counts what an entry expands to against its own limit and hands each count on, for the limit of the export. */
    static final class Limited extends FilterInputStream {

        private final long max;
        private final LongConsumer total;
        private long count;

        Limited(InputStream in, long max, LongConsumer total) {
            super(in);
            this.max = max;
            this.total = total;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            add(value == -1 ? 0 : 1);
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            add(Math.max(read, 0));
            return read;
        }

        private void add(int bytes) {
            count += bytes;
            if (count > max) {
                throw new BackupException(TOO_BIG);
            }
            total.accept(bytes);
        }
    }
}
