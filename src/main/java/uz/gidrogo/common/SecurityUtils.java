package uz.gidrogo.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import uz.gidrogo.security.UserPrincipal;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static UserPrincipal getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }

    public static Long getCurrentUserId() {
        UserPrincipal principal = getCurrentUser();
        return principal != null ? principal.getId() : null;
    }

    public static Long getCurrentFarmId() {
        UserPrincipal principal = getCurrentUser();
        return principal != null ? principal.getFarmId() : TenantContext.getFarmId();
    }
}
