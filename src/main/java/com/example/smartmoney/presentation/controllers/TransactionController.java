package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.CreateTransactionRequest;
import com.example.smartmoney.application.dtos.requests.UpdateTransactionRequest;
import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.dtos.responses.TransactionListResponse;
import com.example.smartmoney.application.dtos.responses.TransactionResponse;
import com.example.smartmoney.application.usecases.transaction.CreateTransactionUseCase;
import com.example.smartmoney.application.usecases.transaction.DeleteTransactionUseCase;
import com.example.smartmoney.application.usecases.transaction.GetTransactionUseCase;
import com.example.smartmoney.application.usecases.transaction.GetUserTransactionsUseCase;
import com.example.smartmoney.application.usecases.transaction.UpdateTransactionUseCase;
import com.example.smartmoney.infrastructure.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Transactions",
    description = "Transaction management APIs for creating, reading, updating, and deleting financial transactions"
)
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {
    
    private final CreateTransactionUseCase createTransactionUseCase;
    private final UpdateTransactionUseCase updateTransactionUseCase;
    private final GetTransactionUseCase getTransactionUseCase;
    private final GetUserTransactionsUseCase getUserTransactionsUseCase;
    private final DeleteTransactionUseCase deleteTransactionUseCase;
    private final JwtTokenProvider jwtTokenProvider;
    
    @Operation(
        summary = "Create new transaction",
        description = "Create a new financial transaction (income or expense) for the authenticated user"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Transaction created successfully",
            content = @Content(schema = @Schema(implementation = TransactionResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid input data"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Invalid or missing token"
        )
    })
    @PostMapping
    public ResponseEntity<CheckResponse<TransactionResponse>> createTransaction(
            @Parameter(description = "Bearer token", required = true, hidden = true)
            @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "Transaction creation data", required = true)
            @Valid @RequestBody CreateTransactionRequest request) {
        
        try {
            Long userId = extractUserIdFromToken(authHeader);
            log.info("Creating transaction for user ID: {}", userId);
            
            TransactionResponse transactionResponse = createTransactionUseCase.execute(userId, request);
            
            CheckResponse<TransactionResponse> response = CheckResponse.<TransactionResponse>builder()
                .success(true)
                .message("Transaction created successfully")
                .data(transactionResponse)
                .build();
            
            log.info("Transaction created successfully with ID: {}", transactionResponse.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            log.error("Failed to create transaction", e);
            
            CheckResponse<TransactionResponse> response = CheckResponse.<TransactionResponse>builder()
                .success(false)
                .message("Failed to create transaction: " + e.getMessage())
                .build();
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
    
    @Operation(
        summary = "Get all user transactions",
        description = "Retrieve all transactions for the authenticated user"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Transactions retrieved successfully",
            content = @Content(schema = @Schema(implementation = TransactionListResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Invalid or missing token"
        )
    })
    @GetMapping
    public ResponseEntity<CheckResponse<TransactionListResponse>> getUserTransactions(
            @Parameter(description = "Bearer token", required = true, hidden = true)
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            Long userId = extractUserIdFromToken(authHeader);
            log.info("Fetching transactions for user ID: {}", userId);
            
            TransactionListResponse transactionListResponse = getUserTransactionsUseCase.execute(userId);
            
            CheckResponse<TransactionListResponse> response = CheckResponse.<TransactionListResponse>builder()
                .success(true)
                .message("Transactions retrieved successfully")
                .data(transactionListResponse)
                .build();
            
            log.info("Retrieved {} transactions for user ID: {}", transactionListResponse.getCount(), userId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to retrieve transactions", e);
            
            CheckResponse<TransactionListResponse> response = CheckResponse.<TransactionListResponse>builder()
                .success(false)
                .message("Failed to retrieve transactions: " + e.getMessage())
                .build();
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @Operation(
        summary = "Get transaction by ID",
        description = "Retrieve a specific transaction by its ID. User must own the transaction."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Transaction retrieved successfully",
            content = @Content(schema = @Schema(implementation = TransactionResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Invalid or missing token"
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Transaction does not belong to user"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Transaction not found"
        )
    })
    @GetMapping("/{id}")
    public ResponseEntity<CheckResponse<TransactionResponse>> getTransaction(
            @Parameter(description = "Bearer token", required = true, hidden = true)
            @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "Transaction ID", required = true)
            @PathVariable Long id) {
        
        try {
            Long userId = extractUserIdFromToken(authHeader);
            log.info("Fetching transaction ID: {} for user ID: {}", id, userId);
            
            TransactionResponse transactionResponse = getTransactionUseCase.execute(userId, id);
            
            CheckResponse<TransactionResponse> response = CheckResponse.<TransactionResponse>builder()
                .success(true)
                .message("Transaction retrieved successfully")
                .data(transactionResponse)
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to retrieve transaction ID: {}", id, e);
            
            CheckResponse<TransactionResponse> response = CheckResponse.<TransactionResponse>builder()
                .success(false)
                .message("Failed to retrieve transaction: " + e.getMessage())
                .build();
            
            HttpStatus status = e.getMessage().contains("not found") ? 
                HttpStatus.NOT_FOUND : 
                e.getMessage().contains("Unauthorized") ? 
                    HttpStatus.FORBIDDEN : 
                    HttpStatus.BAD_REQUEST;
            
            return ResponseEntity.status(status).body(response);
        }
    }
    
    @Operation(
        summary = "Update transaction",
        description = "Update an existing transaction. User must own the transaction. All fields are optional."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Transaction updated successfully",
            content = @Content(schema = @Schema(implementation = TransactionResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid input data"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Invalid or missing token"
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Transaction does not belong to user"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Transaction not found"
        )
    })
    @PutMapping("/{id}")
    public ResponseEntity<CheckResponse<TransactionResponse>> updateTransaction(
            @Parameter(description = "Bearer token", required = true, hidden = true)
            @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "Transaction ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Transaction update data", required = true)
            @Valid @RequestBody UpdateTransactionRequest request) {
        
        try {
            Long userId = extractUserIdFromToken(authHeader);
            log.info("Updating transaction ID: {} for user ID: {}", id, userId);
            
            TransactionResponse transactionResponse = updateTransactionUseCase.execute(userId, id, request);
            
            CheckResponse<TransactionResponse> response = CheckResponse.<TransactionResponse>builder()
                .success(true)
                .message("Transaction updated successfully")
                .data(transactionResponse)
                .build();
            
            log.info("Transaction ID: {} updated successfully", id);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to update transaction ID: {}", id, e);
            
            CheckResponse<TransactionResponse> response = CheckResponse.<TransactionResponse>builder()
                .success(false)
                .message("Failed to update transaction: " + e.getMessage())
                .build();
            
            HttpStatus status = e.getMessage().contains("not found") ? 
                HttpStatus.NOT_FOUND : 
                e.getMessage().contains("Unauthorized") ? 
                    HttpStatus.FORBIDDEN : 
                    HttpStatus.BAD_REQUEST;
            
            return ResponseEntity.status(status).body(response);
        }
    }
    
    @Operation(
        summary = "Delete transaction",
        description = "Delete a transaction by its ID. User must own the transaction."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Transaction deleted successfully"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Invalid or missing token"
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Transaction does not belong to user"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Transaction not found"
        )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<CheckResponse<Void>> deleteTransaction(
            @Parameter(description = "Bearer token", required = true, hidden = true)
            @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "Transaction ID", required = true)
            @PathVariable Long id) {
        
        try {
            Long userId = extractUserIdFromToken(authHeader);
            log.info("Deleting transaction ID: {} for user ID: {}", id, userId);
            
            deleteTransactionUseCase.execute(userId, id);
            
            CheckResponse<Void> response = CheckResponse.<Void>builder()
                .success(true)
                .message("Transaction deleted successfully")
                .build();
            
            log.info("Transaction ID: {} deleted successfully", id);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to delete transaction ID: {}", id, e);
            
            CheckResponse<Void> response = CheckResponse.<Void>builder()
                .success(false)
                .message("Failed to delete transaction: " + e.getMessage())
                .build();
            
            HttpStatus status = e.getMessage().contains("not found") ? 
                HttpStatus.NOT_FOUND : 
                e.getMessage().contains("Unauthorized") ? 
                    HttpStatus.FORBIDDEN : 
                    HttpStatus.BAD_REQUEST;
            
            return ResponseEntity.status(status).body(response);
        }
    }
    
    /**
     * Extract user ID from JWT token in Authorization header
     */
    private Long extractUserIdFromToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtTokenProvider.getUserIdFromToken(token);
        }
        throw new IllegalArgumentException("Invalid Authorization header");
    }
}
