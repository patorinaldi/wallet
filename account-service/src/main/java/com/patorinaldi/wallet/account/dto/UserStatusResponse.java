package com.patorinaldi.wallet.account.dto;

import com.patorinaldi.wallet.account.entity.UserStatus;

import java.util.UUID;

public record UserStatusResponse (
        UUID userId,
        UserStatus status
){
}
