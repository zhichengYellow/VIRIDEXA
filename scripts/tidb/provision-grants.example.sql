-- Review against a separate TiDB 8.5.6+ release schema after migrations.
-- Create SQL users with securely generated passwords in the TiDB console first.
-- Replace schema and the three identity names. This template contains no passwords.
-- Never give the running API the cluster administrator/migrator identity.
GRANT SELECT ON viridexa_tidb_20261031.* TO 'viridexa_ro'@'%';

GRANT SELECT ON viridexa_tidb_20261031.app_user TO 'viridexa_members'@'%';
GRANT SELECT ON viridexa_tidb_20261031.app_role TO 'viridexa_members'@'%';
GRANT SELECT ON viridexa_tidb_20261031.user_role TO 'viridexa_members'@'%';
GRANT SELECT ON viridexa_tidb_20261031.public_member_account TO 'viridexa_members'@'%';
GRANT INSERT(email,display_name,password_hash) ON viridexa_tidb_20261031.app_user TO 'viridexa_members'@'%';
GRANT INSERT(user_id) ON viridexa_tidb_20261031.public_member_account TO 'viridexa_members'@'%';
-- Intentionally NO INSERT/UPDATE/DELETE on user_role, app_role or science records.

GRANT SELECT ON viridexa_tidb_20261031.app_user TO 'viridexa_reviewer'@'%';
GRANT SELECT ON viridexa_tidb_20261031.app_role TO 'viridexa_reviewer'@'%';
GRANT SELECT ON viridexa_tidb_20261031.user_role TO 'viridexa_reviewer'@'%';
GRANT SELECT ON viridexa_tidb_20261031.scientific_candidate TO 'viridexa_reviewer'@'%';
GRANT SELECT ON viridexa_tidb_20261031.scientific_source_version TO 'viridexa_reviewer'@'%';
GRANT SELECT ON viridexa_tidb_20261031.scientific_decision TO 'viridexa_reviewer'@'%';
GRANT SELECT ON viridexa_tidb_20261031.scientific_review_state TO 'viridexa_reviewer'@'%';
GRANT SELECT ON viridexa_tidb_20261031.scientific_sample TO 'viridexa_reviewer'@'%';
GRANT SELECT ON viridexa_tidb_20261031.scientific_lipid_observation TO 'viridexa_reviewer'@'%';
GRANT SELECT ON viridexa_tidb_20261031.scientific_evidence_link TO 'viridexa_reviewer'@'%';
GRANT SELECT ON viridexa_tidb_20261031.source_governance TO 'viridexa_reviewer'@'%';
GRANT SELECT ON viridexa_tidb_20261031.source_license_event TO 'viridexa_reviewer'@'%';
GRANT SELECT ON viridexa_tidb_20261031.plant TO 'viridexa_reviewer'@'%';
GRANT SELECT ON viridexa_tidb_20261031.taxon TO 'viridexa_reviewer'@'%';
GRANT SELECT ON viridexa_tidb_20261031.evidence TO 'viridexa_reviewer'@'%';
GRANT SELECT ON viridexa_tidb_20261031.source_asset TO 'viridexa_reviewer'@'%';
GRANT SELECT ON viridexa_tidb_20261031.review_event TO 'viridexa_reviewer'@'%';
GRANT INSERT ON viridexa_tidb_20261031.scientific_decision TO 'viridexa_reviewer'@'%';
GRANT INSERT ON viridexa_tidb_20261031.scientific_review_state TO 'viridexa_reviewer'@'%';
GRANT UPDATE(state,version) ON viridexa_tidb_20261031.scientific_review_state TO 'viridexa_reviewer'@'%';
GRANT INSERT ON viridexa_tidb_20261031.source_governance TO 'viridexa_reviewer'@'%';
GRANT UPDATE(version) ON viridexa_tidb_20261031.source_governance TO 'viridexa_reviewer'@'%';
GRANT INSERT ON viridexa_tidb_20261031.source_license_event TO 'viridexa_reviewer'@'%';
GRANT UPDATE(license_state) ON viridexa_tidb_20261031.scientific_source_version TO 'viridexa_reviewer'@'%';
GRANT INSERT ON viridexa_tidb_20261031.review_event TO 'viridexa_reviewer'@'%';
GRANT UPDATE(review_state,visibility_state) ON viridexa_tidb_20261031.plant TO 'viridexa_reviewer'@'%';
GRANT UPDATE(identity_state) ON viridexa_tidb_20261031.taxon TO 'viridexa_reviewer'@'%';
