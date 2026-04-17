package com.uniremington.api.convenia.shared.exception;

/**
 * Thrown when a login attempt fails due to wrong email or password.
 *
 * <p>
 * Extends {@link RuntimeException} so it does not need to be declared
 * in method signatures (unchecked exception).
 * </p>
 *
 * <p>
 * The message is intentionally generic — never reveal whether the email
 * or the password was wrong, as that helps attackers enumerate valid emails.
 * </p>
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
