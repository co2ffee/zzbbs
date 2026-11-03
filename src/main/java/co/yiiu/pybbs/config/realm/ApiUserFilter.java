package co.yiiu.pybbs.config.realm;

import org.apache.shiro.web.filter.authc.UserFilter;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 后台页面已经由独立的管理端 SPA 负责，/admin/** 都是 JSON 接口。
 * 没有登录时直接返回 401 + JSON，而不是 302 到登录页（302 的 Location 里还会带上 jsessionid 和内网地址）。
 */
public class ApiUserFilter extends UserFilter {

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws IOException {
        HttpServletResponse res = (HttpServletResponse) response;
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType("application/json;charset=utf-8");
        res.getWriter().write("{\"code\":401,\"description\":\"请先登录\",\"detail\":null}");
        return false;
    }
}
