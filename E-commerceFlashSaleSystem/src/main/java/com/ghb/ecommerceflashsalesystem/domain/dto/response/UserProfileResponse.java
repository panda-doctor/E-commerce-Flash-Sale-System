package com.ghb.ecommerceflashsalesystem.domain.dto.response;

import com.ghb.ecommerceflashsalesystem.domain.entity.UserAccount;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class UserProfileResponse {
    private Long id;
    private String accountName;
    private String displayName;
    private Integer status;
    private LocalDateTime createdAt;

    public static UserProfileResponse from(UserAccount user) {
        return new UserProfileResponse(user.getId(), user.getAccountName(), user.getDisplayName(),
                user.getStatus(), user.getCreatedAt());
    }
}
