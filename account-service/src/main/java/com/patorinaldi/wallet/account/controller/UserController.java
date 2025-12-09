package com.patorinaldi.wallet.account.controller;

import com.patorinaldi.wallet.account.dto.CreateUserRequest;
import com.patorinaldi.wallet.account.dto.UpdateUserRequest;
import com.patorinaldi.wallet.account.dto.UserResponse;
import com.patorinaldi.wallet.account.dto.UserStatusResponse;
import com.patorinaldi.wallet.account.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@RequestBody @Valid CreateUserRequest createUserRequest) {
        log.info("POST /users - Creating user");
        return userService.createUser(createUserRequest);
    }

    @GetMapping("/users/{id}")
    @ResponseStatus(HttpStatus.OK)
    public UserResponse getUserById(@PathVariable UUID id) {
        log.debug("GET /users/{} - Fetching user", id);
        return userService.getUserById(id);
    }

    @GetMapping("/users/email/{email}")
    @ResponseStatus(HttpStatus.OK)
    public UserResponse getUserByEmail(@PathVariable String email) {
        log.debug("GET /users/email/{} - Fetching user", email);
        return userService.getUserByEmail(email);
    }

    @PutMapping("/users/{id}")
    @ResponseStatus(HttpStatus.OK)
    public UserResponse updateUser(@PathVariable UUID id, @RequestBody @Valid UpdateUserRequest updateUserRequest) {
        log.info("PUT /users/{} - Updating user", id);
        return userService.updateUser(id, updateUserRequest);
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.OK)
    public UserResponse deactivateUser(@PathVariable UUID id) {
        log.info("DELETE /users/{} - Deactivating user", id);
        return userService.deactivateUser(id);
    }

    @GetMapping("/users/{id}/status")
    public ResponseEntity<UserStatusResponse> getUserStatus(@PathVariable UUID id) {
        log.debug("GET /users/{}/status - Fetching user status", id);
        return ResponseEntity.ok(userService.getUserStatus(id));
    }
}
