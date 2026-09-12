-- Reset all numbered field accounts to the requested shared initial password.
-- The administrator account is deliberately excluded.
UPDATE app_user user_account
JOIN app_role user_role ON user_role.id = user_account.role_id
SET user_account.password_hash = '$2a$10$oM5gtmcuaQFHcwk2x97ZEOgamrAeGIYvZSsREVAjXVXwNFyWTave2'
WHERE user_role.role_code IN ('GUARD', 'COLLECTOR')
  AND (
      user_account.username BETWEEN 'guard01' AND 'guard10'
      OR user_account.username BETWEEN 'collector01' AND 'collector10'
  );
