package com.cloudstorage.storage.exception;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 全局异常处理——把业务异常与未预期异常统一转成 { code, message } JSON。
 * 本应归 common 模块（由 A 组交付统一错误码表），这里先占位，保证 B 组本地最小可运行版
 * 异常不退化成一坨 500，便于联调与排障；接 A 组后迁移到 common 的 BusinessException + advice。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Map<String, Object> handleBiz(BizException e) {
        return Map.of("code", e.getCode(), "message", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Map<String, Object> handleOther(Exception e) {
        return Map.of("code", 50000, "message", rootMessage(e));
    }

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return cur.getClass().getSimpleName() + ": " + (msg == null ? cur.toString() : msg);
    }
}