package uz.gidrogo.modules.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByPhone(String phone);
    Optional<User> findByEmail(String email);

    @org.springframework.data.jpa.repository.Query("""
        SELECT u FROM User u 
        WHERE LOWER(u.username) = LOWER(:identifier) 
           OR u.phone = :identifier 
           OR u.phone = :altPhone
    """)
    Optional<User> findByUsernameOrPhone(@org.springframework.data.repository.query.Param("identifier") String identifier,
                                         @org.springframework.data.repository.query.Param("altPhone") String altPhone);

    boolean existsByUsername(String username);
    boolean existsByPhone(String phone);
    List<User> findAllByFarmId(Long farmId);
    List<User> findAllByFarmIdAndRole(Long farmId, Role role);
    Optional<User> findByFarmIdAndRole(Long farmId, Role role);
    List<User> findAllByRole(Role role);
    List<User> findAllByRoleOrderByCreatedAtDesc(Role role);
    long countByRole(Role role);
    long countByRoleAndStatus(Role role, String status);
}
