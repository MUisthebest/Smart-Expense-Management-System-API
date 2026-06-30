package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import com.example.smartmoney.domain.models.user.valueobjects.Phone;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.infrastructure.persistence.entities.UserEntity;
import com.example.smartmoney.infrastructure.persistence.mappers.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {
    
    private final JpaUserRepository jpaUserRepository;
    private final UserMapper userMapper;
    
    @Override
    public User save(User user) {
        UserEntity entity = userMapper.toEntity(user);
        UserEntity savedEntity = jpaUserRepository.save(entity);
        return userMapper.toDomain(savedEntity);
    }
    
    @Override
    public Optional<User> findById(UUID id) {
        return jpaUserRepository.findById(id)
            .map(userMapper::toDomain);
    }

    @Override
    public List<User> findAllByIds(Collection<UUID> ids) {
        return jpaUserRepository.findAllById(ids).stream()
                .map(userMapper::toDomain)
                .toList();
    }
    
    @Override
    public Optional<User> findByEmail(Email email) {
        return jpaUserRepository.findByEmail(email.getValue())
            .map(userMapper::toDomain);
    }
    
    @Override
    public Optional<User> findByUsername(String username) {
        return jpaUserRepository.findByUsername(username)
            .map(userMapper::toDomain);
    }
    
    @Override
    public Optional<User> findByPhone(Phone phone) {
        return jpaUserRepository.findByPhone(phone.getValue())
            .map(userMapper::toDomain);
    }

    @Override
    public boolean isVerifiedByEmail(Email email) {
        return jpaUserRepository.isVerifiedByEmail(email.getValue());
    }

    @Override
    public boolean deleteByEmail(Email email) {
        Optional<UserEntity> userOpt = jpaUserRepository.findByEmail(email.getValue());
        if (userOpt.isPresent()) {
            jpaUserRepository.delete(userOpt.get());
            return true;
        }
        return false;
    }

    
    @Override
    public boolean existsByEmail(Email email) {
        return jpaUserRepository.existsByEmail(email.getValue());
    }
    
    @Override
    public boolean existsByUsername(String username) {
        return jpaUserRepository.existsByUsername(username);
    }
    
    @Override
    public boolean existsByPhone(Phone phone) {
        return jpaUserRepository.existsByPhone(phone.getValue());
    }

    @Override
    public boolean existsByPhoneAndIdNot(Phone phone, UUID id) {
        return jpaUserRepository.existsByPhoneAndIdNot(phone.getValue(), id);
    }
//
//    @Override
//    public void markIncomeSetupCompleted(UUID userId, boolean completed) {
//        jpaUserRepository.markIncomeSetupCompleted(userId, completed);
//    }
//
//    @Override
//    public void markFinancialSetupCompleted(UUID userId, boolean completed) {
//        jpaUserRepository.markFinancialSetupCompleted(userId, completed);
//    }
}