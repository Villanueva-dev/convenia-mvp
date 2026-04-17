package com.uniremington.api.convenia.service.impl;

import com.uniremington.api.convenia.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Loads a user's authentication details from the database by email.
 *
 * <p>
 * Spring Security calls this class during the login flow
 * (via {@code AuthenticationManager}) to retrieve the stored user
 * and compare the provided password against the BCrypt hash.
 * </p>
 *
 * <p>
 * The role is mapped to a Spring {@link SimpleGrantedAuthority} with
 * the {@code ROLE_} prefix required by Spring Security's default
 * {@code hasRole()} expressions.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads user details by email address.
     *
     * @param email the email used as the login username
     * @return {@link UserDetails} containing hashed password and authorities
     * @throws UsernameNotFoundException if no user exists with that email
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("No user found with email: " + email));

        // Map the single role to a Spring Security authority.
        // "ROLE_" prefix is required for hasRole("COORDINATOR") to work in @PreAuthorize.
        var authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());

        return User.builder()
                .username(user.getEmail())
                .password(user.getPassword())   // BCrypt hash — Spring compares this internally
                .authorities(List.of(authority))
                .disabled(!user.isActive())      // If active=false, login is rejected
                .build();
    }
}
