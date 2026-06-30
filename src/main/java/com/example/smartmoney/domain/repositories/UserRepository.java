package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import com.example.smartmoney.domain.models.user.valueobjects.Phone;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(UUID id);
    List<User> findAllByIds(Collection<UUID> ids);
    Optional<User> findByEmail(Email email);
    Optional<User> findByUsername(String username);
    Optional<User> findByPhone(Phone phone);
    boolean isVerifiedByEmail(Email email);
    boolean deleteByEmail(Email email);
    boolean existsByEmail(Email email);
    boolean existsByUsername(String username);
    boolean existsByPhone(Phone phone);
    boolean existsByPhoneAndIdNot(Phone phone, UUID id);
//    void markIncomeSetupCompleted(UUID userId, boolean completed);
//    void markFinancialSetupCompleted(UUID userId, boolean completed);
}