package com.example.dynamic.config;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TenantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        System.out.println("request tenantId: " + request.getHeader("tenantId"));
        String tenantId = request.getHeader("tenantId");
        if (StringUtils.isEmpty(tenantId)) {
//            return false;
        }
        // 进入之前先根据请求头设置数据源
        // todo 拼接当前服务对应的数据库名称前缀
        DynamicDataSourceContextHolder.push(tenantId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        // 执行完请求移除数据源
        DynamicDataSourceContextHolder.poll();
    }

}
