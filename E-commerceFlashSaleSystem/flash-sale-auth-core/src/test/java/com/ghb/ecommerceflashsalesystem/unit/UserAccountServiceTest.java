package com.ghb.ecommerceflashsalesystem.unit;

import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.user.dto.request.RegisterRequest;
import com.ghb.ecommerceflashsalesystem.domain.entity.UserAccount;
import com.ghb.ecommerceflashsalesystem.user.mapper.UserAccountMapper;
import com.ghb.ecommerceflashsalesystem.user.mapper.UserSessionMapper;
import com.ghb.ecommerceflashsalesystem.user.service.UserAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAccountServiceTest {
    private UserAccountMapper accountMapper;
    private UserSessionMapper sessionMapper;
    private BCryptPasswordEncoder passwordEncoder;
    private UserAccountService service;

    @BeforeEach
    void setUp() {
        accountMapper = mock(UserAccountMapper.class);
        sessionMapper = mock(UserSessionMapper.class);
        passwordEncoder = new BCryptPasswordEncoder(12);
        service = new UserAccountService(accountMapper, sessionMapper, passwordEncoder);
    }

    @Test
    void registerNormalizesAccountAndStoresOnlyPasswordHash() {
        RegisterRequest request = new RegisterRequest();
        request.setAccountName("Alice.Shop");
        request.setPassword("CorrectHorse!123");
        request.setDisplayName("Alice");

        UserAccount created = service.register(request);
        verify(accountMapper).insert(any(UserAccount.class));
        assertThat(created.getAccountName()).isEqualTo("alice.shop");
        assertThat(created.getPasswordHash()).isNotEqualTo(request.getPassword());
        assertThat(passwordEncoder.matches(request.getPassword(), created.getPasswordHash())).isTrue();
        assertThat(created.getStatus()).isEqualTo(1);
    }

    @Test
    void duplicateNormalizedAccountReturnsConflict() {
        when(accountMapper.insert(any(UserAccount.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertThatThrownBy(() -> service.create("Alice", "CorrectHorse!123", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getResultCode())
                        .isEqualTo(ResultCode.ACCOUNT_NAME_TAKEN));
    }

    @Test
    void missingAccountAndWrongPasswordHaveSameBusinessError() {
        assertThatThrownBy(() -> service.authenticate("unknown", "WrongPassword!123"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getResultCode())
                        .isEqualTo(ResultCode.INVALID_CREDENTIALS));
    }
}
