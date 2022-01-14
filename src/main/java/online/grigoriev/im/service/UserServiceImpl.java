package online.grigoriev.im.service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import online.grigoriev.im.dto.mapper.UserMapper;
import online.grigoriev.im.dto.model.UserDTO;
import online.grigoriev.im.entity.User;
import online.grigoriev.im.exception.UserNotFoundException;
import online.grigoriev.im.model.UserRole;
import online.grigoriev.im.repository.UserRepository;


@Component
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDTO save(final UserDTO userDTO) {
        final User user = new UserMapper().toEntity(userDTO);
        final User savedUser = userRepository.save(user);
        return new UserMapper().toDTO(savedUser);
    }

    @Override
    public List<UserDTO> findByRoles(final String... roles) {
        final Set<UserRole> userRoles = Arrays.stream(roles)
                .map(UserRole::valueOf)
                .collect(Collectors.toSet());
        final List<User> users = userRepository.findByUserRoles(userRoles);
        return users.stream()
                .map(user -> new UserMapper().toDTO(user))
                .collect(Collectors.toList());
    }

    @Override
    public UserDTO findByUsername(final String username) {
        final User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("user with username " + username + " not found"));
        return new UserMapper().toDTO(user);
    }
}
