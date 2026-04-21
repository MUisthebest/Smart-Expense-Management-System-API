package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.infrastructure.persistence.entities.UserIncomeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaUserIncomeRepository extends JpaRepository<UserIncomeEntity, UUID> {
}
