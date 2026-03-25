package com.goldtrading.backend.admin.dto.response;

import com.goldtrading.backend.common.RoleType;
import com.goldtrading.backend.common.UserStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUserResponse(UUID id, String fullName, String email, String phone, String address,
                                String preferredLanguage, RoleType role, UserStatus status, OffsetDateTime createdAt) {}
