package com.uniremington.api.convenia.model.vo;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * Custom principal stored in the Spring Security {@code SecurityContext}.
 *
 * <p>
 * After the {@code JwtAuthenticationFilter} validates a token, it builds
 * a {@code JwtUser} instance from the JWT claims and sets it as the
 * authentication principal. Any service can then retrieve the current
 * user's ID, role, or tenant (university) without hitting the database.
 * </p>
 *
 * <p>
 * This is a Value Object (VO): it is immutable, has no identity, and
 * exists only to carry data extracted from the JWT.
 * </p>
 *
 * @param username      the user's email (JWT {@code subject} claim)
 * @param userId        the database ID of the authenticated user
 * @param role          the user's role as a string (e.g., "COORDINATOR")
 * @param universityId  the tenant ID; {@code null} for ADMIN users
 * @param authorities   the Spring Security granted authorities (derived from role)
 */
@Getter
public class JwtUser implements UserDetails {

    private final String username;
    private final Long userId;
    private final String role;
    private final Long universityId;
    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * Constructs a JwtUser from validated JWT claims.
     *
     * @param username     email extracted from the JWT subject
     * @param userId       user ID extracted from the "user-id" claim
     * @param role         role string extracted from the "role" claim
     * @param universityId university ID from the "university-id" claim (nullable)
     * @param authorities  Spring Security authorities derived from the role
     */
    public JwtUser(
            String username,
            Long userId,
            String role,
            Long universityId,
            Collection<? extends GrantedAuthority> authorities) {
        this.username = username;
        this.userId = userId;
        this.role = role;
        this.universityId = universityId;
        this.authorities = authorities;
    }

    /**
     * Returns an empty string — the password is never stored in the token
     * or needed after authentication.
     */
    @Override
    public String getPassword() {
        return "";
    }
}
