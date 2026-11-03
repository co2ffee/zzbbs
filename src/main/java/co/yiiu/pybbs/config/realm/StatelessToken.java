package co.yiiu.pybbs.config.realm;

import org.apache.shiro.authc.AuthenticationToken;

/**
 * 用HTTP header里的token字符串承载的无状态认证凭据，配合{@link TokenAuthFilter}使用
 */
public class StatelessToken implements AuthenticationToken {

    private final String token;

    public StatelessToken(String token) {
        this.token = token;
    }

    @Override
    public Object getPrincipal() {
        return token;
    }

    @Override
    public Object getCredentials() {
        return token;
    }
}
