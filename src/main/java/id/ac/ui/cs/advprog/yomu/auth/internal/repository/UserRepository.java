package id.ac.ui.cs.advprog.yomu.auth.internal.repository;

import id.ac.ui.cs.advprog.yomu.auth.internal.model.AuthProvider;
import id.ac.ui.cs.advprog.yomu.auth.internal.model.Role;
import id.ac.ui.cs.advprog.yomu.auth.internal.model.User;
import jakarta.annotation.PostConstruct;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS auth_users (
                id UUID PRIMARY KEY,
                username VARCHAR(255) UNIQUE NOT NULL,
                email VARCHAR(255) UNIQUE,
                phone VARCHAR(50),
                display_name VARCHAR(255) NOT NULL,
                password VARCHAR(255),
                role VARCHAR(50) NOT NULL,
                provider VARCHAR(50) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
    }

    private final RowMapper<User> userRowMapper = (rs, rowNum) -> User.builder()
            .id(rs.getObject("id", UUID.class))
            .username(rs.getString("username"))
            .email(rs.getString("email"))
            .phone(rs.getString("phone"))
            .displayName(rs.getString("display_name"))
            .password(rs.getString("password"))
            .role(Role.valueOf(rs.getString("role")))
            .provider(AuthProvider.valueOf(rs.getString("provider")))
            .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
            .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
            .build();

    public void save(User user) {
        String sql = """
            INSERT INTO auth_users (id, username, email, phone, display_name, password, role, provider, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        jdbcTemplate.update(sql,
                user.getId(), user.getUsername(), user.getEmail(), user.getPhone(),
                user.getDisplayName(), user.getPassword(), user.getRole().name(),
                user.getProvider().name(), user.getCreatedAt(), user.getUpdatedAt());
    }

    public void update(User user) {
        String sql = """
            UPDATE auth_users 
            SET username = ?, email = ?, phone = ?, display_name = ?, password = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
        """;
        jdbcTemplate.update(sql,
                user.getUsername(), user.getEmail(), user.getPhone(),
                user.getDisplayName(), user.getPassword(), user.getId());
    }

    public Optional<User> findById(UUID id) {
        String sql = "SELECT * FROM auth_users WHERE id = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, id);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<User> findByUsernameOrEmail(String identifier) {
        String sql = "SELECT * FROM auth_users WHERE username = ? OR email = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, identifier, identifier);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean existsByUsername(String username) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_users WHERE username = ?", Integer.class, username);
        return count != null && count > 0;
    }

    public boolean existsByEmail(String email) {
        if (email == null || email.isEmpty()) return false;
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_users WHERE email = ?", Integer.class, email);
        return count != null && count > 0;
    }

    public void deleteById(UUID id) {
        jdbcTemplate.update("DELETE FROM auth_users WHERE id = ?", id);
    }
}
