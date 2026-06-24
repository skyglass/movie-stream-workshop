package com.ivanfranchin.moviesapi.userextra.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "users")
public class UserExtra {
    private static final String SYNTHETIC_EMAIL_DOMAIN = "skycomposer.net";

    @Id
    @Column(nullable = false, updatable = false)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String avatar;

    public UserExtra(String username) {
        this(username, emailForUsername(username));
    }

    public UserExtra(String username, String email) {
        this.username = username;
        this.email = email;
        this.avatar = username;
    }

    public static String emailForUsername(String username) {
        return username + "@" + SYNTHETIC_EMAIL_DOMAIN;
    }
}
