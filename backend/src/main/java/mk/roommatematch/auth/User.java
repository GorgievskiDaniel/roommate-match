package mk.roommatematch.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "session_version", nullable = false)
    private int sessionVersion;

    protected User() {
    }

    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public int getSessionVersion() {
        return sessionVersion;
    }

    public void verifyEmail() {
        this.emailVerified = true;
    }

    /** Login tokens issued before this no longer work. */
    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        endSessions();
    }

    /** Every login token issued so far stops working. */
    public void endSessions() {
        this.sessionVersion++;
    }
}
