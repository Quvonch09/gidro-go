package uz.gidrogo.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import uz.gidrogo.modules.auth.Role;
import uz.gidrogo.modules.auth.User;

import java.util.Collection;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal implements UserDetails {
    private Long id;
    private Long farmId;
    private Role role;
    private String identifier; // username or phone
    private String password;
    private boolean active;

    public static UserPrincipal create(User user) {
        String identifier = user.getUsername() != null ? user.getUsername() : user.getPhone();
        return UserPrincipal.builder()
                .id(user.getId())
                .farmId(user.getFarmId())
                .role(user.getRole())
                .identifier(identifier)
                .password(user.getPasswordHash())
                .active("ACTIVE".equalsIgnoreCase(user.getStatus()))
                .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return identifier;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
