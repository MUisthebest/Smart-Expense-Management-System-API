package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.infrastructure.persistence.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaUserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByPhone(String phone);
    @Query("SELECT u.emailVerified FROM UserEntity u WHERE u.email = :email")
    Boolean isVerifiedByEmail(@Param("email") String email);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByPhone(String phone);
    boolean existsByPhoneAndIdNot(String phone, UUID id);

//    @Modifying(clearAutomatically = true, flushAutomatically = true)
//    @Query("""
//    UPDATE UserEntity u
//    SET u.incomeSetupCompleted = :completed
//    WHERE u.id = :userId
//""")
//    int markIncomeSetupCompleted(
//            @Param("userId") UUID userId,
//            @Param("completed") boolean completed
//    );

//    @Modifying(clearAutomatically = true, flushAutomatically = true)
//    @Query("""
//    UPDATE UserEntity u
//    SET u.financialSetupCompleted = :completed
//    WHERE u.id = :userId
//""")
//    int markFinancialSetupCompleted(
//            @Param("userId") UUID userId,
//            @Param("completed") boolean completed
//    );
}