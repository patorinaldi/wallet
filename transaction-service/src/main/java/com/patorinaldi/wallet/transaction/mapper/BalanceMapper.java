package com.patorinaldi.wallet.transaction.mapper;

import com.patorinaldi.wallet.transaction.dto.BalanceResponse;
import com.patorinaldi.wallet.transaction.entity.WalletBalance;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BalanceMapper {

    @Mapping(source = "updatedAt", target = "lastUpdated")
    BalanceResponse toResponse(WalletBalance walletBalance);
}
