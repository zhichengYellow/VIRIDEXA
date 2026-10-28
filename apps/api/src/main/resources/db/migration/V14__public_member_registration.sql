INSERT INTO app_role(role_key,description) VALUES('MEMBER','Public account; no scientific review or administrative permissions');
-- The account writer may EXECUTE this fixed operation, never INSERT arbitrary roles.
CREATE PROCEDURE register_public_member(IN member_email VARCHAR(254),IN member_name VARCHAR(120),IN member_hash VARCHAR(255))
SQL SECURITY DEFINER
BEGIN
 DECLARE new_user_id BIGINT UNSIGNED;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 INSERT INTO app_user(email,display_name,password_hash) VALUES(member_email,member_name,member_hash);
 SET new_user_id=LAST_INSERT_ID();
 INSERT INTO user_role(user_id,role_id) SELECT new_user_id,id FROM app_role WHERE role_key='MEMBER';
 IF ROW_COUNT()<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Public membership unavailable'; END IF;
 COMMIT;
 SELECT new_user_id AS id;
END;
