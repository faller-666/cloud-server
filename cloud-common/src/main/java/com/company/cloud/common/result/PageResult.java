package com.company.cloud.common.result;

import java.io.Serializable;
import java.util.List;

/**
 * 统一分页返回结构（三组共用，契约的一部分）。
 *
 * @param total 总记录数
 * @param list  当前页数据
 */
public record PageResult<T>(long total, List<T> list) implements Serializable {

    public static <T> PageResult<T> of(long total, List<T> list) {
        return new PageResult<>(total, list);
    }
}
