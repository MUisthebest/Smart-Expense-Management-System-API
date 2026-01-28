package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import com.example.smartmoney.domain.models.user.valueobjects.Phone;
import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(Long id);
    Optional<User> findByEmail(Email email);
    Optional<User> findByUsername(String username);
    Optional<User> findByPhone(Phone phone);
    boolean existsByEmail(Email email);
    boolean existsByUsername(String username);
    boolean existsByPhone(Phone phone);
    boolean existsByPhoneAndIdNot(Phone phone, Long id);
}