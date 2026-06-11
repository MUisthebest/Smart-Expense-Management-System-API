package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.budgets.CreateBudgetRequest;
import com.example.smartmoney.application.dtos.requests.budgets.CreateBulkBudgetsRequest;
import com.example.smartmoney.application.dtos.requests.budgets.UpdateBudgetRequest;
import com.example.smartmoney.application.dtos.responses.BudgetItemResponse;
import com.example.smartmoney.application.dtos.responses.BudgetListResponse;
import com.example.smartmoney.application.dtos.responses.BulkBudgetsResponse;
import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.usecases.budget.*;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Budgets",
    description = "Budget management APIs for creating, reading, updating, and deleting financial budgets"
)
@SecurityRequirement(name = "bearerAuth")
public class BudgetController {
    
    private final CreateBudgetUseCase createBudgetUseCase;
    private final CreateBulkBudgetsUseCase createBulkBudgetsUseCase;
    private final UpdateBulkBudgetsUseCase updateBulkBudgetsUseCase;
    private final UpsertBulkBudgetsUseCase upsertBulkBudgetsUseCase;
    private final GetBudgetListUseCase getBudgetListUseCase;
    private final GetBudgetDetailUseCase getBudgetDetailUseCase;
    private final UpdateBudgetUseCase updateBudgetUseCase;
    private final DeleteBudgetUseCase deleteBudgetUseCase;
    private final JwtTokenProvider jwtTokenProvider;
    
    @Operation(
        summary = "Create new budget",
        description = "Create a new budget for a category in a specific month. Each user can have only one budget per category per month."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Budget created successfully",
            content = @Content(schema = @Schema(implementation = BudgetItemResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid input data or budget already exists for this category/month"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Invalid or missing token"
        )
    })
    @PostMapping
    public ResponseEntity<CheckResponse<BudgetItemResponse>> createBudget(
            @Parameter(description = "Bearer token", required = true, hidden = true)
            @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "Budget creation data", required = true)
            @Valid @RequestBody CreateBudgetRequest request) {
        
        try {
            UUID userId = extractUserIdFromToken(authHeader);
            log.info("Creating budget for user ID: {} with category: {}, month: {}, year: {}", 
                    userId, request.getCategory(), request.getMonth(), request.getYear());
            
            BudgetItemResponse budgetResponse = createBudgetUseCase.execute(userId, request);
            
            CheckResponse<BudgetItemResponse> response = CheckResponse.<BudgetItemResponse>builder()
                .success(true)
                .message("Budget created successfully")
                .data(budgetResponse)
                .build();
            
            log.info("Budget created successfully with ID: {}", budgetResponse.getBudgetId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            log.error("Failed to create budget", e);
            
            CheckResponse<BudgetItemResponse> response = CheckResponse.<BudgetItemResponse>builder()
                .success(false)
                .message("Failed to create budget: " + e.getMessage())
                .build();
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
    
    @Operation(
        summary = "Create budgets for multiple categories",
        description = "Create budgets for multiple categories at once with the same month and year. Each budget item must have a unique category."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Budgets created successfully (some or all)",
            content = @Content(schema = @Schema(implementation = BulkBudgetsResponse.class))
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
    @PostMapping("/bulk")
    public ResponseEntity<CheckResponse<BulkBudgetsResponse>> createBulkBudgets(
            @Parameter(description = "Bearer token", required = true, hidden = true)
            @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "Bulk budget creation data", required = true)
            @Valid @RequestBody CreateBulkBudgetsRequest request) {
        
        try {
            UUID userId = extractUserIdFromToken(authHeader);
            log.info("Creating bulk budgets for user ID: {} with {} items for month: {}, year: {}", 
                    userId, request.getBudgets().size(), request.getMonth(), request.getYear());
            
            BulkBudgetsResponse bulkResponse = createBulkBudgetsUseCase.execute(userId, request);
            
            CheckResponse<BulkBudgetsResponse> response = CheckResponse.<BulkBudgetsResponse>builder()
                .success(true)
                .message(String.format("Successfully created %d budgets", bulkResponse.getTotalCreated()))
                .data(bulkResponse)
                .build();
            
            log.info("Bulk budgets created successfully: {} created, {} failed", 
                    bulkResponse.getTotalCreated(), 
                    bulkResponse.getFailedItems() != null ? bulkResponse.getFailedItems().size() : 0);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            log.error("Failed to create bulk budgets", e);
            
            CheckResponse<BulkBudgetsResponse> response = CheckResponse.<BulkBudgetsResponse>builder()
                .success(false)
                .message("Failed to create bulk budgets: " + e.getMessage())
                .build();
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @Operation(
            summary = "Update budgets for multiple categories",
            description = "Update budgets for multiple categories at once with the same month and year. Each budget item must have a unique category."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Budgets updated successfully",
                    content = @Content(schema = @Schema(implementation = BulkBudgetsResponse.class))
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
                    responseCode = "404",
                    description = "Some budgets were not found"
            )
    })
    @PutMapping("/bulk")
    public ResponseEntity<CheckResponse<BulkBudgetsResponse>> updateBulkBudgets(
            @Parameter(description = "Bearer token", required = true, hidden = true)
            @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "Bulk budget update data", required = true)
            @Valid @RequestBody CreateBulkBudgetsRequest request) {

        try {
            UUID userId = extractUserIdFromToken(authHeader);

            log.info(
                    "Updating bulk budgets for user ID: {} with {} items for month: {}, year: {}",
                    userId,
                    request.getBudgets().size(),
                    request.getMonth(),
                    request.getYear()
            );

            BulkBudgetsResponse bulkResponse = updateBulkBudgetsUseCase.execute(userId, request);

            CheckResponse<BulkBudgetsResponse> response = CheckResponse.<BulkBudgetsResponse>builder()
                    .success(true)
                    .message(String.format("Successfully updated %d budgets", bulkResponse.getTotalCreated()))
                    .data(bulkResponse)
                    .build();

            log.info(
                    "Bulk budgets updated successfully: {} updated, {} failed",
                    bulkResponse.getTotalCreated(),
                    bulkResponse.getFailedItems() != null ? bulkResponse.getFailedItems().size() : 0
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to update bulk budgets", e);

            CheckResponse<BulkBudgetsResponse> response = CheckResponse.<BulkBudgetsResponse>builder()
                    .success(false)
                    .message("Failed to update bulk budgets: " + e.getMessage())
                    .build();

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
    
    @Operation(
        summary = "Get budgets for a month",
        description = "Retrieve all budgets for the authenticated user for a specific month and year"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Budgets retrieved successfully",
            content = @Content(schema = @Schema(implementation = BudgetListResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid month or year parameters"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Invalid or missing token"
        )
    })
    @GetMapping
    public ResponseEntity<CheckResponse<BudgetListResponse>> getBudgetList(
            @Parameter(description = "Bearer token", required = true, hidden = true)
            @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "Month (1-12)", required = true)
            @RequestParam Integer month,
            @Parameter(description = "Year", required = true)
            @RequestParam Integer year) {
        
        try {
            UUID userId = extractUserIdFromToken(authHeader);
            log.info("Fetching budgets for user ID: {} with month: {}, year: {}", userId, month, year);
            
            BudgetListResponse budgetListResponse = getBudgetListUseCase.execute(userId, month, year);
            
            CheckResponse<BudgetListResponse> response = CheckResponse.<BudgetListResponse>builder()
                .success(true)
                .message("Budgets retrieved successfully")
                .data(budgetListResponse)
                .build();
            
            log.info("Retrieved {} budgets for user ID: {}", 
                    budgetListResponse.getItems().size(), userId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to retrieve budgets", e);
            
            CheckResponse<BudgetListResponse> response = CheckResponse.<BudgetListResponse>builder()
                .success(false)
                .message("Failed to retrieve budgets: " + e.getMessage())
                .build();
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
    
    @Operation(
        summary = "Get budget details",
        description = "Retrieve details of a specific budget by its ID"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Budget retrieved successfully",
            content = @Content(schema = @Schema(implementation = BudgetItemResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Invalid or missing token"
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Budget does not belong to user"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Budget not found"
        )
    })
    @GetMapping("/{id}")
    public ResponseEntity<CheckResponse<BudgetItemResponse>> getBudgetDetail(
            @Parameter(description = "Bearer token", required = true, hidden = true)
            @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "Budget ID", required = true)
            @PathVariable UUID id) {
        
        try {
            UUID userId = extractUserIdFromToken(authHeader);
            log.info("Fetching budget ID: {} for user ID: {}", id, userId);
            
            BudgetItemResponse budgetResponse = getBudgetDetailUseCase.execute(id, userId);
            
            CheckResponse<BudgetItemResponse> response = CheckResponse.<BudgetItemResponse>builder()
                .success(true)
                .message("Budget retrieved successfully")
                .data(budgetResponse)
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to retrieve budget ID: {}", id, e);
            
            CheckResponse<BudgetItemResponse> response = CheckResponse.<BudgetItemResponse>builder()
                .success(false)
                .message("Failed to retrieve budget: " + e.getMessage())
                .build();
            
            HttpStatus status = e.getMessage().contains("not found") ? 
                HttpStatus.NOT_FOUND : 
                e.getMessage().contains("does not belong") ? 
                    HttpStatus.FORBIDDEN : 
                    HttpStatus.BAD_REQUEST;
            
            return ResponseEntity.status(status).body(response);
        }
    }
    
    @Operation(
        summary = "Update budget",
        description = "Update the amount limit of an existing budget"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Budget updated successfully",
            content = @Content(schema = @Schema(implementation = BudgetItemResponse.class))
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
            description = "Forbidden - Budget does not belong to user"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Budget not found"
        )
    })
    @PutMapping("/{id}")
    public ResponseEntity<CheckResponse<BudgetItemResponse>> updateBudget(
            @Parameter(description = "Bearer token", required = true, hidden = true)
            @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "Budget ID", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Budget update data", required = true)
            @Valid @RequestBody UpdateBudgetRequest request) {
        
        try {
            UUID userId = extractUserIdFromToken(authHeader);
            log.info("Updating budget ID: {} for user ID: {}", id, userId);
            
            BudgetItemResponse budgetResponse = updateBudgetUseCase.execute(id, userId, request);
            
            CheckResponse<BudgetItemResponse> response = CheckResponse.<BudgetItemResponse>builder()
                .success(true)
                .message("Budget updated successfully")
                .data(budgetResponse)
                .build();
            
            log.info("Budget ID: {} updated successfully", id);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to update budget ID: {}", id, e);
            
            CheckResponse<BudgetItemResponse> response = CheckResponse.<BudgetItemResponse>builder()
                .success(false)
                .message("Failed to update budget: " + e.getMessage())
                .build();
            
            HttpStatus status = e.getMessage().contains("not found") ? 
                HttpStatus.NOT_FOUND : 
                e.getMessage().contains("does not belong") ? 
                    HttpStatus.FORBIDDEN : 
                    HttpStatus.BAD_REQUEST;
            
            return ResponseEntity.status(status).body(response);
        }
    }
    
    @Operation(
        summary = "Delete budget",
        description = "Delete a budget by its ID. User must own the budget."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Budget deleted successfully"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Invalid or missing token"
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Budget does not belong to user"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Budget not found"
        )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<CheckResponse<Void>> deleteBudget(
            @Parameter(description = "Bearer token", required = true, hidden = true)
            @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "Budget ID", required = true)
            @PathVariable UUID id) {
        
        try {
            UUID userId = extractUserIdFromToken(authHeader);
            log.info("Deleting budget ID: {} for user ID: {}", id, userId);
            
            deleteBudgetUseCase.execute(id, userId);
            
            CheckResponse<Void> response = CheckResponse.<Void>builder()
                .success(true)
                .message("Budget deleted successfully")
                .build();
            
            log.info("Budget ID: {} deleted successfully", id);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to delete budget ID: {}", id, e);
            
            CheckResponse<Void> response = CheckResponse.<Void>builder()
                .success(false)
                .message("Failed to delete budget: " + e.getMessage())
                .build();
            
            HttpStatus status = e.getMessage().contains("not found") ? 
                HttpStatus.NOT_FOUND : 
                e.getMessage().contains("does not belong") ? 
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
}
