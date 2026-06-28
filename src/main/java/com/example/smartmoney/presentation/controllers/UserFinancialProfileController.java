package com.example.smartmoney.presentation.controllers;

/*
import com.example.smartmoney.application.dtos.requests.userfinancialprofile.CreateUserFinancialProfileRequest;
import com.example.smartmoney.application.dtos.requests.userfinancialprofile.UpdateUserFinancialProfileRequest;
import com.example.smartmoney.application.dtos.responses.UserFinancialProfileResponse;
import com.example.smartmoney.application.usecases.userprofile.CreateUserFinancialProfileUseCase;
import com.example.smartmoney.application.usecases.userprofile.DeleteUserFinancialProfileUseCase;
import com.example.smartmoney.application.usecases.userprofile.GetUserFinancialProfileUseCase;
import com.example.smartmoney.application.usecases.userprofile.UpdateUserFinancialProfileUseCase;
import com.example.smartmoney.application.dtos.responses.CheckResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/user-financial-profile")
public class UserFinancialProfileController {

    private final CreateUserFinancialProfileUseCase createUseCase;
    private final UpdateUserFinancialProfileUseCase updateUseCase;
    private final GetUserFinancialProfileUseCase getUseCase;
    private final DeleteUserFinancialProfileUseCase deleteUseCase;

    private String actorId(Authentication auth) {

        return auth.getName();
    }

    public UserFinancialProfileController(
            CreateUserFinancialProfileUseCase createUseCase,
            UpdateUserFinancialProfileUseCase updateUseCase,
            GetUserFinancialProfileUseCase getUseCase,
            DeleteUserFinancialProfileUseCase deleteUseCase
    ) {
        this.createUseCase = createUseCase;
        this.updateUseCase = updateUseCase;
        this.getUseCase = getUseCase;
        this.deleteUseCase = deleteUseCase;
    }

    @Operation(
            summary = "Create user financial profile",
            description = "Create financial profile information for the authenticated user"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User financial profile created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "409", description = "User financial profile already exists")
    })
    @PostMapping
    public CheckResponse<UserFinancialProfileResponse> create(
            Authentication auth,
            @Valid @RequestBody CreateUserFinancialProfileRequest req
    ) {
        UserFinancialProfileResponse data = createUseCase.execute(actorId(auth), req);

        return CheckResponse.<UserFinancialProfileResponse>builder()
                .success(true)
                .message("Created")
                .data(data)
                .build();
    }

    @Operation(
            summary = "Update user financial profile",
            description = "Update financial profile information for the authenticated user"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User financial profile updated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "User financial profile not found"),
            @ApiResponse(responseCode = "409", description = "User financial profile already exists")
    })
    @PutMapping
    public CheckResponse<UserFinancialProfileResponse> update(
            Authentication auth,
            @Valid @RequestBody UpdateUserFinancialProfileRequest req
    ) {
        UserFinancialProfileResponse data = updateUseCase.execute(actorId(auth), req);

        return CheckResponse.<UserFinancialProfileResponse>builder()
                .success(true)
                .message("Updated")
                .data(data)
                .build();
    }

    @Operation(
            summary = "Get user financial profile",
            description = "Get financial profile information of the authenticated user"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User financial profile retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "User financial profile not found")
    })
    @GetMapping()
    public CheckResponse<UserFinancialProfileResponse> get(Authentication auth) {
        UserFinancialProfileResponse data = getUseCase.execute(actorId(auth));

        if (data == null) {
            return CheckResponse.<UserFinancialProfileResponse>builder()
                    .success(false)
                    .message("Failed")
                    .data(null)
                    .build();
        }

        return CheckResponse.<UserFinancialProfileResponse>builder()
                .success(true)
                .message("Success")
                .data(data)
                .build();
    }

    @Operation(
            summary = "Delete user financial profile",
            description = "Delete financial profile information of the authenticated user"
    )
    @DeleteMapping
    public CheckResponse<Void> delete(Authentication auth) {

        deleteUseCase.execute(actorId(auth));

        return CheckResponse.<Void>builder()
                .success(true)
                .message("Deleted")
                .data(null)
                .build();
    }

}
*/
