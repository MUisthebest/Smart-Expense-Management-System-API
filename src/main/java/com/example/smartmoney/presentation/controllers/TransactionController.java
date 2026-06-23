package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.transactions.AnalyticsRequest;
import com.example.smartmoney.application.dtos.requests.transactions.CreateTransactionRequest;
import com.example.smartmoney.application.dtos.requests.transactions.TransactionFilterRequest;
import com.example.smartmoney.application.dtos.requests.transactions.UpdateTransactionRequest;
import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.dtos.responses.TransactionListResponse;
import com.example.smartmoney.application.dtos.responses.TransactionResponse;
import com.example.smartmoney.application.dtos.responses.WeeklyTransactionResponse;
import com.example.smartmoney.application.usecases.transaction.*;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.application.dtos.responses.TransactionAnalyticsResponse;
import com.example.smartmoney.application.dtos.responses.WeeklyTransactionResponse;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
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
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;

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
    private final GetTransactionAnalyticsUseCase getTransactionAnalyticsUseCase;
    
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
            UUID userId = extractUserIdFromToken(authHeader);
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
        description = "Retrieve all transactions for the authenticated user with optional filters and pagination. " +
                     "Filters include amount range (minAmount, maxAmount), date range (startDate, endDate in format dd/MM/yyyy HH:mm), " +
                     "category, and transaction type. Sorting supports fields: date, amount, category, type, description (default: date DESC). " +
                     "Pagination defaults to page=0, size=10."
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
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Bad Request - Invalid filter parameters"
        )
    })
    @GetMapping
    public ResponseEntity<CheckResponse<TransactionListResponse>> getUserTransactions(
            @Parameter(description = "Bearer token", required = true, hidden = true)
            @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "Minimum amount filter")
            @RequestParam(required = false) BigDecimal minAmount,
            @Parameter(description = "Maximum amount filter")
            @RequestParam(required = false) BigDecimal maxAmount,
            @Parameter(description = "Start date filter (format: dd/MM/yyyy HH:mm)")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "End date filter (format: dd/MM/yyyy HH:mm)")
            @RequestParam(required = false) String endDate,
            @Parameter(description = "Category filter")
            @RequestParam(required = false) Category category,
            @Parameter(description = "Transaction type filter (INCOME or EXPENSE)")
            @RequestParam(required = false) TransactionType type,
            @Parameter(description = "Sort field (date, amount, category, type, description). Default: date")
            @RequestParam(required = false, defaultValue = "date") String sortBy,
            @Parameter(description = "Sort order (ASC or DESC). Default: DESC")
            @RequestParam(required = false, defaultValue = "DESC") String sortOrder,
            @Parameter(description = "Page number (0-indexed, default: 0)")
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "Page size (default: 10)")
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        
        try {
            UUID userId = extractUserIdFromToken(authHeader);
            log.info("Fetching transactions for user ID: {} with filters - minAmount: {}, maxAmount: {}, startDate: {}, endDate: {}, category: {}, type: {}, sortBy: {}, sortOrder: {}, page: {}, size: {}",
                    userId, minAmount, maxAmount, startDate, endDate, category, type, sortBy, sortOrder, page, size);
            
            // Build filter request
            TransactionFilterRequest filter = TransactionFilterRequest.builder()
                    .minAmount(minAmount)
                    .maxAmount(maxAmount)
                    .startDate(startDate)
                    .endDate(endDate)
                    .category(category)
                    .type(type)
                    .sortBy(sortBy)
                    .sortOrder(sortOrder)
                    .page(page)
                    .size(size)
                    .build();
            
            TransactionListResponse transactionListResponse = getUserTransactionsUseCase.execute(userId, filter);
            
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
            
            HttpStatus status = e.getMessage() != null && e.getMessage().contains("Invalid date format") ?
                HttpStatus.BAD_REQUEST :
                HttpStatus.INTERNAL_SERVER_ERROR;
            
            return ResponseEntity.status(status).body(response);
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
            @PathVariable UUID id) {
        
        try {
            UUID userId = extractUserIdFromToken(authHeader);
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
            @PathVariable UUID id,
            @Parameter(description = "Transaction update data", required = true)
            @Valid @RequestBody UpdateTransactionRequest request) {
        
        try {
            UUID userId = extractUserIdFromToken(authHeader);
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
            @PathVariable UUID id) {
        
        try {
            UUID userId = extractUserIdFromToken(authHeader);
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
    public UUID extractUserIdFromToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtTokenProvider.getUserIdFromToken(token);
        }
        throw new IllegalArgumentException("Invalid Authorization header");
    }

    /*
    * Get analytics - amount of expenses and income per year and their proportion, if necessary.
    * */
    @Operation(
            summary = "Get transaction analytics",
            description = "Retrieve financial analytics including monthly income/expense stats and transaction type proportions for a specific year."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Analytics retrieved successfully",
                    content = @Content(schema = @Schema(implementation = TransactionAnalyticsResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing token"
            )
    })
    @PostMapping("/analytics")
    public ResponseEntity<CheckResponse<TransactionAnalyticsResponse>> getAnalytics(
            @Parameter(description = "Bearer token", required = true, hidden = true)
            @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "Analytics for specific month, including the latest 3 months.")
            @RequestBody(required = false)AnalyticsRequest request) {

        try {
            UUID userId = extractUserIdFromToken(authHeader);

            // If no input month, get the current year by default
            int targetMonth = (request.getMonth() == null) ? java.time.LocalDateTime.now().getMonthValue() - 1 : request.getMonth();

            log.info("Fetching analytics for user ID: {} and month, year: {}", userId, request.getMonth(), request.getYear());

            // 3. Thực thi UseCase
            TransactionAnalyticsResponse analyticsResponse = getTransactionAnalyticsUseCase.execute(userId, request.getYear(), targetMonth);

            // 4. Trả về kết quả theo cấu trúc CheckResponse
            CheckResponse<TransactionAnalyticsResponse> response = CheckResponse.<TransactionAnalyticsResponse>builder()
                    .success(true)
                    .message("Analytics retrieved successfully")
                    .data(analyticsResponse)
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to retrieve analytics", e);

            CheckResponse<TransactionAnalyticsResponse> response = CheckResponse.<TransactionAnalyticsResponse>builder()
                    .success(false)
                    .message("Failed to retrieve analytics: " + e.getMessage())
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/weekly-summary")
    @Operation(
        summary = "Get weekly summary for current and previous week",
        description = "Returns income/expense totals and expense by category for current week (Mon-Sun) and previous week"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Weekly summary retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<CheckResponse<WeeklyTransactionResponse>> getWeeklySummary(
            @RequestHeader("Authorization") String authHeader) {
        try {
            UUID userId = extractUserIdFromToken(authHeader);
            
            // 1. Xác định khoảng thời gian của 2 tuần
            LocalDate today = LocalDate.now();
            LocalDate currentWeekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            LocalDate currentWeekEnd = currentWeekStart.plusDays(6);
            LocalDate previousWeekStart = currentWeekStart.minusWeeks(1);
            LocalDate previousWeekEnd = previousWeekStart.plusDays(6);
            
            // 2. Format date theo yêu cầu của TransactionFilterRequest (dd/MM/yyyy HH:mm)
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            String currentStartStr = currentWeekStart.atStartOfDay().format(formatter);
            String currentEndStr = currentWeekEnd.atTime(23, 59).format(formatter);
            String prevStartStr = previousWeekStart.atStartOfDay().format(formatter);
            String prevEndStr = previousWeekEnd.atTime(23, 59).format(formatter);
            
            // 3. Lấy transactions cho từng tuần
            TransactionFilterRequest currentFilter = TransactionFilterRequest.builder()
                    .startDate(currentStartStr)
                    .endDate(currentEndStr)
                    .build();
            TransactionListResponse currentWeekResponse = getUserTransactionsUseCase.execute(userId, currentFilter);
            
            TransactionFilterRequest prevFilter = TransactionFilterRequest.builder()
                    .startDate(prevStartStr)
                    .endDate(prevEndStr)
                    .build();
            TransactionListResponse prevWeekResponse = getUserTransactionsUseCase.execute(userId, prevFilter);
            
            // 4. Build dữ liệu cho từng tuần (không kèm transactions)
            WeeklyTransactionResponse.WeekData currentData = buildWeekData(currentWeekStart, currentWeekEnd, 
                    currentWeekResponse.getTransactions());
            WeeklyTransactionResponse.WeekData previousData = buildWeekData(previousWeekStart, previousWeekEnd, 
                    prevWeekResponse.getTransactions());
            
            // 5. Tạo response
            WeeklyTransactionResponse response = WeeklyTransactionResponse.builder()
                    .currentWeek(currentData)
                    .previousWeek(previousData)
                    .build();
            
            return ResponseEntity.ok(CheckResponse.<WeeklyTransactionResponse>builder()
                    .success(true)
                    .message("Weekly summary retrieved successfully")
                    .data(response)
                    .build());
            
        } catch (Exception e) {
            log.error("Failed to get weekly summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CheckResponse.<WeeklyTransactionResponse>builder()
                            .success(false)
                            .message("Failed to retrieve weekly summary: " + e.getMessage())
                            .build());
        }
    }

    private WeeklyTransactionResponse.WeekData buildWeekData(LocalDate start, LocalDate end, List<TransactionResponse> transactions) {
        BigDecimal totalIncome = transactions.stream()
                .filter(tx -> tx.getType() == TransactionType.INCOME)
                .map(TransactionResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalExpense = transactions.stream()
                .filter(tx -> tx.getType() == TransactionType.EXPENSE)
                .map(TransactionResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Map<String, BigDecimal> expenseByCategory = transactions.stream()
                .filter(tx -> tx.getType() == TransactionType.EXPENSE)
                .collect(Collectors.groupingBy(
                    tx -> tx.getCategory() != null ? tx.getCategory().name() : "UNCATEGORIZED",
                    Collectors.reducing(BigDecimal.ZERO, TransactionResponse::getAmount, BigDecimal::add)
                ));
        
        return WeeklyTransactionResponse.WeekData.builder()
                .startDate(start)
                .endDate(end)
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .expenseByCategory(expenseByCategory)
                .build();
    }
}
