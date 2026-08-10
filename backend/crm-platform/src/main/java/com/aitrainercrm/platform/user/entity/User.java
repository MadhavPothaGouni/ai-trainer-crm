package com.aitrainercrm.platform.user.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import com.aitrainercrm.platform.role.entity.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    public enum Status {
        PENDING_VERIFICATION, ACTIVE, SUSPENDED, DEACTIVATED
    }

    /**
     * Stored lowercase/trimmed at write time (see UserService) so a
     * case-insensitive-in-intent email column stays enforceable by a plain
     * unique constraint instead of a functional index.
     */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.PENDING_VERIFICATION;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "manager_id")
    private UUID managerId;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(length = 60)
    private String timezone = "UTC";

    @Column(length = 20)
    private String locale = "en-US";

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled = false;

    /** TOTP shared secret, encrypted at rest by AttributeConverter (see security/crypto). Null until MFA is enabled. */
    @Column(name = "mfa_secret")
    private String mfaSecret;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** Soft delete: preserves FK-referencing history (orders, invoices, audit trail) instead of cascading deletes. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    public User(String email, String passwordHash, String firstName, String lastName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public String getFullName() {
        return "%s %s".formatted(firstName, lastName).trim();
    }

    public boolean isAccountLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }
}
