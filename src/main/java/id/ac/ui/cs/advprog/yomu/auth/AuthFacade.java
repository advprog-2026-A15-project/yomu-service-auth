package id.ac.ui.cs.advprog.yomu.auth;

import id.ac.ui.cs.advprog.yomu.auth.internal.repository.UserRepository;
import id.ac.ui.cs.advprog.yomu.shared.security.JwtService;
import id.ac.ui.cs.advprog.yomu.shared.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AuthFacade {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    public Optional<UserDto> getUserById(UUID id) {
        return userRepository.findById(id).map(user -> UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .displayName(user.getDisplayName())
                .role(user.getRole().name())
                .build());
    }

    public boolean isTokenValid(String token) {
        try {
            String username = jwtService.extractUsername(token);
            return userRepository.findByUsernameOrEmail(username)
                    .map(user -> jwtService.isTokenValid(token))
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }
}
