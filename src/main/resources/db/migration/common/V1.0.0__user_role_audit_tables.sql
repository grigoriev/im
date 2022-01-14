CREATE SEQUENCE im_user_id_seq;
CREATE TABLE im_user
(
    id         INTEGER PRIMARY KEY,
    first_name TEXT NOT NULL,
    last_name  TEXT NOT NULL,
    username   TEXT NOT NULL,
    email      TEXT NOT NULL
);

CREATE SEQUENCE im_role_id_seq;
CREATE TABLE im_role
(
    id   INTEGER PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE SEQUENCE im_user_role_id_seq;
CREATE TABLE im_user_role
(
    id      INTEGER PRIMARY KEY,
    user_id INTEGER REFERENCES im_user (id),
    role_id INTEGER REFERENCES im_role (id)
);

CREATE SEQUENCE im_audit_log_id_seq;
CREATE TABLE im_audit_log
(
    id      INTEGER PRIMARY KEY,
    action  TEXT NOT NULL,
    time    TIME NOT NULL,
    log     TEXT NOT NULL,
    user_id INTEGER REFERENCES im_user (id)
);
