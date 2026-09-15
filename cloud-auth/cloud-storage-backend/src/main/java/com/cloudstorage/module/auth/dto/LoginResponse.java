package com.cloudstorage.module.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 登录响应（对标任务书 6.1 登录响应约定）
 * {
 *   "code":0, "message":"ok",
 *   "data":{ "accessToken":"...", "refreshToken":"...", "user":{...} }
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private Map<String, Object> user;
}