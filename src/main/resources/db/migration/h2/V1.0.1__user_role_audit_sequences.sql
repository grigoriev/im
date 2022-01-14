ALTER TABLE im_user ALTER COLUMN id SET DEFAULT im_user_id_seq.nextval;
ALTER TABLE im_role ALTER COLUMN id SET DEFAULT im_role_id_seq.nextval;
ALTER TABLE im_user_role ALTER COLUMN id SET DEFAULT im_user_role_id_seq.nextval;
ALTER TABLE im_audit_log ALTER COLUMN id SET DEFAULT im_audit_log_id_seq.nextval;

INSERT INTO im_role VALUES (im_role_id_seq.nextval, 'ADMIN');
INSERT INTO im_role VALUES (im_role_id_seq.nextval, 'USER');