package co.yiiu.pybbs.interceptor;

import co.yiiu.pybbs.service.IUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 用户端内容必须登录后才能查看：/api/** 除了登录/注册/激活等入口外，没有有效 token 一律返回 401 + JSON。
 * 前端收到 401 会清掉本地登录态并跳到 /login。
 * 开关：site.login_required（默认 true），设为 false 恢复成游客可浏览。
 */
@Component
public class LoginRequiredInterceptor implements HandlerInterceptor {

    /** 未登录也要能访问的接口：登录、注册、验证码短信、站点公开配置、邮箱激活、第三方登录换 token */
    private static final Set<String> PUBLIC = new HashSet<>(Arrays.asList(
            "/api/login", "/api/register", "/api/sms_code", "/api/mobile_login",
            "/api/site", "/api/active", "/api/oauth/session"));

    @Value("${site.login_required:true}")
    private boolean loginRequired;
    @Resource
    private IUserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!loginRequired) return true;
        // CORS 预检请求不带 token
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI();
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (PUBLIC.contains(path)) return true;
        String token = request.getHeader("token");
        if (!StringUtils.isEmpty(token) && userService.selectByToken(token) != null) return true;
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"description\":\"请先登录\",\"detail\":null}");
        return false;
    }
}
