package com.example.smartmoney.domain.exceptions;

public class EntityNotFoundException extends DomainException {
    public EntityNotFoundException(String entityName, Long id) {
        super(String.format("%s with id %d not found", entityName, id), "ENTITY_NOT_FOUND");
    }
    
    public EntityNotFoundException(String entityName, String identifier) {
        super(String.format("%s with identifier %s not found", entityName, identifier), "ENTITY_NOT_FOUND");
    }
}