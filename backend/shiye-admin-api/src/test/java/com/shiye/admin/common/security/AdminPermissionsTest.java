package com.shiye.admin.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminPermissionsTest {

    @Test
    void superAdminCanAccessEverything() {
        assertTrue(AdminPermissions.allows("SUPER_ADMIN", "GET", "/admin/api/v1/admins"));
        assertTrue(AdminPermissions.allows("SUPER_ADMIN", "PUT", "/admin/api/v1/settings"));
        assertTrue(AdminPermissions.allows("SUPER_ADMIN", "POST", "/admin/api/v1/models/test"));
        assertTrue(AdminPermissions.allows("SUPER_ADMIN", "DELETE", "/admin/api/v1/eval/cases/1"));
        assertTrue(AdminPermissions.allows("SUPER_ADMIN", "PUT", "/admin/api/v1/budget"));
    }

    @Test
    void operatorCanRunBusinessWritesButNotPrivilegedModules() {
        assertTrue(AdminPermissions.allows("OPERATOR", "PUT", "/admin/api/v1/users/1/quota"));
        assertTrue(AdminPermissions.allows("OPERATOR", "POST", "/admin/api/v1/imports/1/retry"));
        assertTrue(AdminPermissions.allows("OPERATOR", "POST", "/admin/api/v1/eval/runs"));
        assertTrue(AdminPermissions.allows("OPERATOR", "PUT", "/admin/api/v1/teams/1/quota"));
        assertFalse(AdminPermissions.allows("OPERATOR", "GET", "/admin/api/v1/settings"));
        assertFalse(AdminPermissions.allows("OPERATOR", "PUT", "/admin/api/v1/models/chat"));
        assertFalse(AdminPermissions.allows("OPERATOR", "GET", "/admin/api/v1/admins"));
        assertFalse(AdminPermissions.allows("OPERATOR", "POST", "/admin/api/v1/admins"));
        assertFalse(AdminPermissions.allows("OPERATOR", "PUT", "/admin/api/v1/budget"));
    }

    @Test
    void viewerIsReadOnly() {
        assertTrue(AdminPermissions.allows("VIEWER", "GET", "/admin/api/v1/users"));
        assertTrue(AdminPermissions.allows("VIEWER", "GET", "/admin/api/v1/audit-logs/export"));
        assertFalse(AdminPermissions.allows("VIEWER", "PUT", "/admin/api/v1/users/1/status"));
        assertFalse(AdminPermissions.allows("VIEWER", "POST", "/admin/api/v1/imports/1/retry"));
        assertFalse(AdminPermissions.allows("VIEWER", "GET", "/admin/api/v1/settings"));
    }

    @Test
    void unknownRoleIsDenied() {
        assertFalse(AdminPermissions.allows("GUEST", "GET", "/admin/api/v1/overview"));
        assertFalse(AdminPermissions.allows(null, "GET", "/admin/api/v1/overview"));
    }

    @Test
    void legacyAdminRoleNormalizesToSuperAdmin() {
        assertTrue(AdminPermissions.isKnownRole("admin"));
        assertTrue(AdminPermissions.allows("admin", "GET", "/admin/api/v1/admins"));
    }

    @Test
    void unsafePathsAreRejected() {
        assertTrue(AdminJwtAuthFilter.isUnsafePath("/admin/api/v1/%61dmins"));
        assertTrue(AdminJwtAuthFilter.isUnsafePath("/admin/api/v1/admins;foo"));
        assertTrue(AdminJwtAuthFilter.isUnsafePath("/admin/api/v1//admins"));
        assertFalse(AdminJwtAuthFilter.isUnsafePath("/admin/api/v1/admins"));
    }
}
