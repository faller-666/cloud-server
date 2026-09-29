package com.company.cloud.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * App 检查更新返回体：无更新时仅返回 {@code {"hasUpdate":false}}，update 为 null 不输出。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppUpdateVO(boolean hasUpdate, AppUpdateInfo update) {
}