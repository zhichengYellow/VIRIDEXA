-- An insert-only enrollment cannot name or grant an arbitrary role.
-- Default MySQL registration continues using V14's fixed MEMBER procedure.
CREATE TABLE public_member_account (
 user_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
 enrolled_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_public_member_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB;
