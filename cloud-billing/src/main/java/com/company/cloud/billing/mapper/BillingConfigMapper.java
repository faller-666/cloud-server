package com.company.cloud.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.cloud.billing.entity.BillingConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * billing_config 数据访问。单行配置（id=1）。
 */
@Mapper
public interface BillingConfigMapper extends BaseMapper<BillingConfig> {
}