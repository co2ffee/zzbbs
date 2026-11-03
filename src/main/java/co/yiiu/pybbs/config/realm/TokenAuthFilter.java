package co.yiiu.pybbs.config.realm;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;
import org.springframework.util.StringUtils;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * 可选的token header认证过滤器，配合ShiroConfig里"/admin/**"的"tokenAuth, user"过滤链使用：
 * - 请求没带token header：直接放行，交给后面的user过滤器走原有的session/cookie认证路径
 * - 带了token header：走Shiro认证流程（会调用MyShiroRealm），成功/失败都放行给后面的user过滤器判断
 */
public class TokenAuthFilter extends AuthenticatingFilter {

    @Override
    protected AuthenticationToken createToken(ServletRequest request, ServletResponse response) {
        String token = ((HttpServletRequest) request).getHeader("token");
        return new StatelessToken(token);
    }

    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) {
        String token = ((HttpServletRequest) request).getHeader("token");
        if (StringUtils.isEmpty(token)) {
            return true;
        }
        try {
            return executeLogin(request, response);
        } catch (Exception e) {
            return true;
        }
    }

    // isAccessAllowed在所有路径下都返回true，这个方法实际不会被调用到，只是满足抽象方法的实现要求
    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) {
        return true;
    }

    @Override
    protected boolean onLoginSuccess(AuthenticationToken token, Subject subject, ServletRequest request, ServletResponse response) {
        return true;
    }

    @Override
    protected boolean onLoginFailure(AuthenticationToken token, AuthenticationException e, ServletRequest request, ServletResponse response) {
        // token无效，交给后面的user过滤器判断（没有已认证身份会被拦到登录页）
        return true;
    }
}
