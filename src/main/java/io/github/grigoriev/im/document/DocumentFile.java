package io.github.grigoriev.im.document;

import java.util.Arrays;
import java.util.Objects;

/**
 * The file of a business document.
 *
 * @param filename the file name
 * @param mediaType PDF, JPEG or PNG
 * @param content the bytes
 */
public record DocumentFile(String filename, String mediaType, byte[] content) {

    /** Keeps a copy of the bytes, so the file never changes from outside. */
    public DocumentFile {
        content = content.clone();
    }

    /**
     * Returns a copy of the bytes.
     *
     * @return the bytes
     */
    @Override
    public byte[] content() {
        return content.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DocumentFile that && filename.equals(that.filename) && mediaType.equals(that.mediaType)
                && Arrays.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filename, mediaType, Arrays.hashCode(content));
    }

    @Override
    public String toString() {
        return "DocumentFile[" + filename + ", " + mediaType + ", " + content.length + " bytes]";
    }
}
