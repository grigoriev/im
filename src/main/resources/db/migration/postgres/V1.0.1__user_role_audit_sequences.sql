ALTER TABLE im_user ALTER COLUMN id SET DEFAULT nextval('im_user_id_seq');
ALTER TABLE im_role ALTER COLUMN id SET DEFAULT nextval('im_role_id_seq');
ALTER TABLE im_user_role ALTER COLUMN id SET DEFAULT nextval('im_user_role_id_seq');
ALTER TABLE im_audit_log ALTER COLUMN id SET DEFAULT nextval('im_audit_log_id_seq');

ALTER SEQUENCE im_user_id_seq OWNED BY im_user.id;
ALTER SEQUENCE im_role_id_seq OWNED BY im_role.id;
ALTER SEQUENCE im_user_role_id_seq OWNED BY im_user_role.id;
ALTER SEQUENCE im_audit_log_id_seq OWNED BY im_audit_log.id;
