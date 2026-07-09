package com.bot.whatsappbotservice.storefront;

import java.io.Serializable;

/**
 * The storefront's session-stored principal, distinct from {@code security.AppUserPrincipal}
 * (vendor {@code TenantUser} accounts). A {@link Customer} has no password/role — only a phone
 * number verified by OTP — so this deliberately doesn't implement {@code UserDetails}: there's no
 * {@code AuthenticationManager}/{@code DaoAuthenticationProvider} in the OTP flow to require it,
 * and the token's authorities are supplied directly at construction, not derived from this class.
 */
public class CustomerPrincipal implements Serializable {

    private final Long customerId;
    private final Long tenantId;
    private final String phoneNumber;
    private final String preferredLanguageCode;

    public CustomerPrincipal(Long customerId, Long tenantId, String phoneNumber, String preferredLanguageCode) {
        this.customerId = customerId;
        this.tenantId = tenantId;
        this.phoneNumber = phoneNumber;
        this.preferredLanguageCode = preferredLanguageCode;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    /** Captured at login time — a language changed mid-session via {@code ?lang=} isn't reflected
     * here until the customer logs in again; that override always takes precedence anyway. */
    public String getPreferredLanguageCode() {
        return preferredLanguageCode;
    }
}
