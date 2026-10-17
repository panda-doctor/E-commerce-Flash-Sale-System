package com.ghb.ecommerceflashsalesystem.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.common.util.IdGenerator;
import com.ghb.ecommerceflashsalesystem.user.dto.request.AdminUserCreateRequest;
import com.ghb.ecommerceflashsalesystem.user.dto.request.AdminUserUpdateRequest;
import com.ghb.ecommerceflashsalesystem.user.dto.request.RegisterRequest;
import com.ghb.ecommerceflashsalesystem.common.api.PageResponse;
import com.ghb.ecommerceflashsalesystem.user.dto.response.UserProfileResponse;
import com.ghb.ecommerceflashsalesystem.domain.entity.UserAccount;
import com.ghb.ecommerceflashsalesystem.user.mapper.UserAccountMapper;
import com.ghb.ecommerceflashsalesystem.user.mapper.UserSessionMapper;
// P4 收官清理：反向镜像已随旧库下线退役
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "module.auth", name = "mode", havingValue = "db")
public class UserAccountService {
    private static final int ACTIVE = 1;
    private static final int DISABLED = 0;
    private static final int DELETED = 2;

    private final UserAccountMapper userAccountMapper;
    private final UserSessionMapper userSessionMapper;
    private final PasswordEncoder passwordEncoder;
    /** P4 Day 6：auth 权威库在 flash_sale_auth，写后镜像到旧库副本供另外三个运行时做会话校验 */
    // P4 收官清理：authMirror 字段与 6 处镜像调用已删除（旧库已 DROP）

    @Transactional
    public UserAccount register(RegisterRequest request) {
        return create(request.getAccountName(), request.getPassword(), request.getDisplayName());
    }

    @Transactional
    public UserAccount create(String accountName, String password, String displayName) {
        validatePasswordBytes(password);
        UserAccount user = new UserAccount();
        user.setId(IdGenerator.nextId());
        user.setAccountName(normalizeAccountName(accountName));
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(normalizeDisplayName(displayName));
        user.setStatus(ACTIVE);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(user.getCreatedAt());
        try {
            userAccountMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.ACCOUNT_NAME_TAKEN, "账号名已被使用");
        }

        return user;
    }

    public UserAccount authenticate(String accountName, String password) {
        UserAccount user = userAccountMapper.selectOne(new QueryWrapper<UserAccount>()
                .eq("account_name", normalizeAccountName(accountName))
                .eq("status", ACTIVE).last("LIMIT 1"));
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ResultCode.INVALID_CREDENTIALS, "账号名或密码错误");
        }
        return user;
    }

    public UserAccount getActive(Long id) {
        UserAccount user = userAccountMapper.selectById(id);
        if (user == null || !Integer.valueOf(ACTIVE).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号不可用，请重新登录");
        }
        return user;
    }

    public UserProfileResponse profile(Long id) {
        return UserProfileResponse.from(getActive(id));
    }

    public PageResponse<UserProfileResponse> page(long page, long pageSize, String keyword, Integer status) {
        long safePage = Math.max(1, page);
        long safeSize = Math.max(1, Math.min(100, pageSize));
        QueryWrapper<UserAccount> query = new QueryWrapper<>();
        query.ne("status", DELETED);
        if (status != null && (status == ACTIVE || status == DISABLED)) query.eq("status", status);
        if (keyword != null && !keyword.isBlank()) {
            String term = keyword.trim();
            query.and(group -> group.like("account_name", term).or().like("display_name", term));
        }
        query.orderByDesc("created_at");
        Page<UserAccount> result = userAccountMapper.selectPage(new Page<>(safePage, safeSize), query);
        return new PageResponse<>(result.getRecords().stream().map(UserProfileResponse::from).toList(),
                result.getCurrent(), result.getSize(), result.getTotal(), result.getPages());
    }

    public UserProfileResponse adminGet(Long id) {
        UserAccount user = userAccountMapper.selectById(id);
        if (user == null || Integer.valueOf(DELETED).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return UserProfileResponse.from(user);
    }

    @Transactional
    public UserProfileResponse adminCreate(AdminUserCreateRequest request) {
        return UserProfileResponse.from(create(request.getAccountName(), request.getPassword(), request.getDisplayName()));
    }

    @Transactional
    public UserProfileResponse adminUpdate(Long id, AdminUserUpdateRequest request) {
        UserAccount user = findNotDeleted(id);
        if (request.getDisplayName() != null) user.setDisplayName(normalizeDisplayName(request.getDisplayName()));
        if (request.getStatus() != null) {
            int status = request.getStatus();
            user.setStatus(status);
            if (status == DISABLED) {
                LocalDateTime now = LocalDateTime.now();
                userSessionMapper.revokeAllForUser(id, now);

            }
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            validatePasswordBytes(request.getPassword());
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            LocalDateTime now = LocalDateTime.now();
            userSessionMapper.revokeAllForUser(id, now);

        }
        user.setUpdatedAt(LocalDateTime.now());
        userAccountMapper.updateById(user);

        return UserProfileResponse.from(user);
    }

    @Transactional
    public void softDelete(Long id) {
        UserAccount user = findNotDeleted(id);
        user.setStatus(DELETED);
        user.setDeletedAt(LocalDateTime.now());
        user.setUpdatedAt(user.getDeletedAt());
        userAccountMapper.updateById(user);
        userSessionMapper.revokeAllForUser(id, user.getDeletedAt());

    }

    private UserAccount findNotDeleted(Long id) {
        UserAccount user = userAccountMapper.selectById(id);
        if (user == null || Integer.valueOf(DELETED).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private String normalizeAccountName(String accountName) {
        return accountName.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDisplayName(String displayName) {
        return displayName == null || displayName.isBlank() ? null : displayName.trim();
    }

    private void validatePasswordBytes(String password) {
        if (password == null || password.length() < 10 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "密码需至少 10 个字符且 UTF-8 长度不超过 72 字节");
        }
    }
}
