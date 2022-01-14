package online.grigoriev.im.service;

import java.util.List;

import online.grigoriev.im.dto.model.UserDTO;

public interface UserService {
    UserDTO save(UserDTO userDTO);

    List<UserDTO> findByRoles(String... roles);

    UserDTO findByUsername(String username);
}
