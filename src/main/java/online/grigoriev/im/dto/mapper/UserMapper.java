package online.grigoriev.im.dto.mapper;

import java.util.stream.Collectors;

import online.grigoriev.im.dto.model.UserDTO;
import online.grigoriev.im.entity.Role;
import online.grigoriev.im.entity.User;
import online.grigoriev.im.model.UserRole;

public class UserMapper {
    public UserDTO toDTO(final User user) {
        final UserDTO userDTO = new UserDTO();

        userDTO.setFirstName(user.getFirstName());
        userDTO.setLastName(user.getLastName());
        userDTO.setUsername(user.getUsername());
        userDTO.setEmail(user.getEmail());
        userDTO.setRoles(
                user.getRoles().stream()
                        .map(Role::getName)
                        .map(UserRole::name)
                        .collect(Collectors.toList())
        );

        return userDTO;
    }

    public User toEntity(final UserDTO userDTO) {
        final User user = new User();

        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setUsername(userDTO.getUsername());
        user.setEmail(userDTO.getEmail());
        user.setRoles(
                userDTO.getRoles().stream()
                        .map(UserRole::valueOf)
                        .map(userRole -> new Role(null, userRole))
                        .collect(Collectors.toSet())
        );

        return user;
    }
}
