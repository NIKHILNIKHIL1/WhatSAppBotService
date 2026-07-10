package com.bot.whatsappbotservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.tenant.TenantUser;
import com.bot.whatsappbotservice.tenant.TenantUserRepository;
import com.bot.whatsappbotservice.tenant.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class SuperAdminBootstrapTest {

    private TenantUserRepository repository;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        repository = mock(TenantUserRepository.class);
    }

    private static MockEnvironment env(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }

    @Test
    void seedsTenantlessSuperAdminWhenMissing() {
        when(repository.existsByEmailIgnoreCase("root@platform.io")).thenReturn(false);

        new SuperAdminBootstrap(repository, encoder, env("local"), "root@platform.io", "s3cret").run(null);

        ArgumentCaptor<TenantUser> captor = ArgumentCaptor.forClass(TenantUser.class);
        verify(repository).save(captor.capture());
        TenantUser admin = captor.getValue();
        assertThat(admin.getRole()).isEqualTo(UserRole.SUPER_ADMIN);
        assertThat(admin.getTenant()).isNull();
        assertThat(admin.getEmail()).isEqualTo("root@platform.io");
        // Stored hashed, never verbatim.
        assertThat(admin.getPasswordHash()).isNotEqualTo("s3cret");
        assertThat(encoder.matches("s3cret", admin.getPasswordHash())).isTrue();
    }

    @Test
    void neverOverwritesAnExistingAccount() {
        when(repository.existsByEmailIgnoreCase("root@platform.io")).thenReturn(true);

        new SuperAdminBootstrap(repository, encoder, env("local"), "root@platform.io", "changed").run(null);

        verify(repository, never()).save(any());
    }

    @Test
    void missingCredentialsOnlyWarnInLocalProfile() {
        assertThatCode(() -> new SuperAdminBootstrap(repository, encoder, env("local"), "", "").run(null))
                .doesNotThrowAnyException();
        verify(repository, never()).save(any());
    }

    @Test
    void missingCredentialsFailStartupInProdAndDocker() {
        assertThatThrownBy(() -> new SuperAdminBootstrap(repository, encoder, env("prod"), "", "").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPER_ADMIN_EMAIL");
        assertThatThrownBy(() -> new SuperAdminBootstrap(repository, encoder, env("docker"), "", "").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPER_ADMIN_PASSWORD");
    }
}
