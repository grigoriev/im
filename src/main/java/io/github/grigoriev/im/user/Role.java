package io.github.grigoriev.im.user;

/** Role of a user (docs/PLAN.md, section 5). */
public enum Role {

    /** The freelancer: customers, invoices, sending, payments, reports. */
    USER,

    /** Everything of {@link #USER}, plus settings, users, audit log and backup status. */
    ADMIN;

    /**
     * Returns the Spring Security authority of the role.
     *
     * @return for example {@code ROLE_ADMIN}
     */
    public String authority() {
        return "ROLE_" + name();
    }
}
