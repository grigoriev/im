package io.github.grigoriev.im.invoice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Minimal Office files for the tests: a ZIP that names the main part of its type. */
public final class SampleOffice {

    private SampleOffice() {
    }

    /**
     * Returns a minimal Excel workbook.
     *
     * @return the bytes of an XLSX file
     */
    public static byte[] workbook() {
        return zip("xl/workbook.xml");
    }

    /**
     * Returns a minimal Word document.
     *
     * @return the bytes of a DOCX file
     */
    public static byte[] document() {
        return zip("word/document.xml");
    }

    private static byte[] zip(String mainPart) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.putNextEntry(new ZipEntry(mainPart));
            zip.write("<root/>".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
