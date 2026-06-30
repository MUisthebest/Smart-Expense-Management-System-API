package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.user.enums.Focus_Mode;
import com.example.smartmoney.infrastructure.persistence.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
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

    /** Active, notification-enabled users whose focus trait is one of the digest-friendly modes (P2-F). */
    @Query("SELECT u FROM UserEntity u WHERE u.active = true AND u.notificationEnabled = true "
            + "AND u.focusMode IN :modes")
    List<UserEntity> findForDigest(@Param("modes") Collection<Focus_Mode> modes);

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