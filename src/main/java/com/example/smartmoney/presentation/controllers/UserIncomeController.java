package com.example.smartmoney.presentation.controllers;

/*
import com.example.smartmoney.application.dtos.requests.userincome.CreateUserIncomeRequest;
import com.example.smartmoney.application.dtos.requests.userincome.UpdateUserIncomeRequest;
import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.dtos.responses.userincome.UserIncomeResponse;
import com.example.smartmoney.application.usecases.userincome.CreateUserIncomeUseCase;
import com.example.smartmoney.application.usecases.userincome.DeleteUserIncomeUseCase;
import com.example.smartmoney.application.usecases.userincome.GetUserIncomeUseCase;
import com.example.smartmoney.application.usecases.userincome.UpdateUserIncomeUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user-income")
@RequiredArgsConstructor
@Tag(name = "User Income", description = "CRUD APIs for current user's income profile")
@SecurityRequirement(name = "bearerAuth")
public class UserIncomeController {

    private final CreateUserIncomeUseCase createUserIncomeUseCase;
    private final GetUserIncomeUseCase getUserIncomeUseCase;
    private final UpdateUserIncomeUseCase updateUserIncomeUseCase;
    private final DeleteUserIncomeUseCase deleteUserIncomeUseCase;

    private String actorId(Authentication auth) {
        return auth.getName();
    }

    @Operation(summary = "Create my user income")
    @PostMapping("/me")
    public CheckResponse<UserIncomeResponse> create(
            Authentication auth,
            @Valid @RequestBody CreateUserIncomeRequest req
    ) {
        UserIncomeResponse data = createUserIncomeUseCase.execute(actorId(auth), req);
        return CheckResponse.<UserIncomeResponse>builder()
                .success(true)
                .message("Created")
                .data(data)
                .build();
    }

    @Operation(summary = "Get my user income")
    @GetMapping("/me")
    public CheckResponse<UserIncomeResponse> get(Authentication auth) {
        UserIncomeResponse data = getUserIncomeUseCase.execute(actorId(auth));
        return CheckResponse.<UserIncomeResponse>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
    }

    @Operation(summary = "Update my user income")
    @PutMapping("/me")
    public CheckResponse<UserIncomeResponse> update(
            Authentication auth,
            @Valid @RequestBody UpdateUserIncomeRequest req
    ) {
        UserIncomeResponse data = updateUserIncomeUseCase.execute(actorId(auth), req);
        return CheckResponse.<UserIncomeResponse>builder()
                .success(true)
                .message("Updated")
                .data(data)
                .build();
    }

    @Operation(summary = "Delete my user income")
    @DeleteMapping("/me")
    public CheckResponse<Void> delete(Authentication auth) {
        deleteUserIncomeUseCase.execute(actorId(auth));
        return CheckResponse.<Void>builder()
                .success(true)
                .message("Deleted")
                .build();
    }
}
*/
