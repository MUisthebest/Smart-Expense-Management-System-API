package com.example.smartmoney.infrastructure.persistence.mappers;

import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.infrastructure.persistence.entities.TransactionEntity;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-03-02T17:25:13+0700",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17.0.17 (Eclipse Adoptium)"
)
@Component
public class TransactionMapperImpl extends TransactionMapper {

    @Override
    public TransactionEntity toEntity(Transaction domain) {
        if ( domain == null ) {
            return null;
        }

        TransactionEntity transactionEntity = new TransactionEntity();

        transactionEntity.setId( domain.getId() );
        transactionEntity.setUserId( domain.getUserId() );
        transactionEntity.setType( domain.getType() );
        transactionEntity.setCategory( domain.getCategory() );
        transactionEntity.setDescription( domain.getDescription() );
        transactionEntity.setDate( domain.getDate() );
        transactionEntity.setCreatedAt( domain.getCreatedAt() );
        transactionEntity.setUpdatedAt( domain.getUpdatedAt() );

        transactionEntity.setAmount( mapAmountToEntity(domain.getAmount()) );

        afterMappingToEntity( transactionEntity, domain );

        return transactionEntity;
    }

    @Override
    public Transaction toDomain(TransactionEntity entity) {
        if ( entity == null ) {
            return null;
        }

        Transaction.TransactionBuilder transaction = Transaction.builder();

        transaction.id( entity.getId() );
        transaction.userId( entity.getUserId() );
        transaction.type( entity.getType() );
        transaction.category( entity.getCategory() );
        transaction.description( entity.getDescription() );
        transaction.date( entity.getDate() );
        transaction.createdAt( entity.getCreatedAt() );
        transaction.updatedAt( entity.getUpdatedAt() );

        transaction.amount( mapAmountToDomain(entity.getAmount()) );

        return transaction.build();
    }
}
