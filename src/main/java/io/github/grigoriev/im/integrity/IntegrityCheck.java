package io.github.grigoriev.im.integrity;

import java.util.List;

/** Checks one kind of kept data: its files against their SHA-256 and seal. */
public interface IntegrityCheck {

    /**
     * Checks all of it.
     *
     * @return what is changed or broken, empty if all is in order
     */
    List<Damage> damaged();
}
