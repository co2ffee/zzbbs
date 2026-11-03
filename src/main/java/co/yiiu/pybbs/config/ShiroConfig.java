package co.yiiu.pybbs.config;

import co.yiiu.pybbs.config.realm.MyCredentialsMatcher;
import co.yiiu.pybbs.config.realm.MyShiroRealm;
import co.yiiu.pybbs.config.realm.TokenAuthFilter;
import co.yiiu.pybbs.service.ISystemConfigService;
import org.apache.shiro.codec.Base64;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.filter.authc.FormAuthenticationFilter;
import org.apache.shiro.web.mgt.CookieRememberMeManager;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.servlet.SimpleCookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by tomoya.
 * Copyright (c) 2018, All Rights Reserved.
 * https://atjiu.github.io
 */
@Configuration
public class ShiroConfig {

    private final Logger log = LoggerFactory.getLogger(ShiroConfig.class);

    @Resource
    private MyShiroRealm myShiroRealm;
    @Resource
    private ISystemConfigService systemConfigService;

    @Bean
    public ShiroFilterFactoryBean shiroFilterFactoryBean(DefaultWebSecurityManager defaultWebSecurityManager) {
        log.info("开始配置shiroFilter...");
        ShiroFilterFactoryBean shiroFilterFactoryBean = new ShiroFilterFactoryBean();
        shiroFilterFactoryBean.setSecurityManager(defaultWebSecurityManager);

        //拦截器. 用LinkedHashMap保证声明顺序，Shiro按声明顺序从上到下匹配，更具体的路径要排在/admin/**这种宽泛规则前面
        Map<String, String> map = new LinkedHashMap<>();

        // 配置不会被拦截的链接 顺序判断  相关静态资源
        map.put("/static/**", "anon");
        // 登录验证的地址不能跟打开页面的地址一样，否则shiro是先验证用户名密码，失败了才会验证验证码
        map.put("/admin/login", "anon");
        map.put("/admin/api/login", "anon");
        map.put("/adminlogin", "anon");
        map.put("/admin/logout", "anon");
        map.put("/admin/no_auth", "anon");

        //<!-- 过滤链定义，从上向下顺序执行，一般将/**放在最为下边 -->:这是一个坑呢，一不小心代码就不好使了;

        //<!-- authc:所有url都必须认证通过才可以访问; user: 表示rememberMe后就可以访问 anon:所有url都都可以匿名访问 {@link org.apache.shiro.web.filter.mgt.DefaultFilter}-->
        // 这里原来给permission/role/system/admin_user单独配了没带权限值的"perms"过滤器，Shiro遇到这种裸配置
        // 会直接401，不管登没登录/有没有权限，4块页面谁都打不开。实际权限判断本来就靠各controller的
        // @RequiresPermissions注解，所以直接去掉，让它们跟其它页面一样走下面的/admin/**

        // tokenAuth：带token header就按无状态方式认证，不带就直接放行给user过滤器走原有session/cookie流程，两条路径并存
        map.put("/admin/**", "tokenAuth, apiUser");
//        map.put("/admin/comment/**", "user");
//        map.put("/admin/sensitive_word/**", "user");
//        map.put("/admin/tag/**", "user");
//        map.put("/admin/topic/**", "user");
//        map.put("/admin/user/**", "user");

        Map<String, javax.servlet.Filter> filters = new HashMap<>();
        filters.put("tokenAuth", new TokenAuthFilter());
        // 未登录返回 401 JSON（替换默认的 user 过滤器的 302 跳转）
        filters.put("apiUser", new co.yiiu.pybbs.config.realm.ApiUserFilter());
        shiroFilterFactoryBean.setFilters(filters);

        shiroFilterFactoryBean.setFilterChainDefinitionMap(map);

        // 如果不设置默认会自动寻找Web工程根目录下的"/login.jsp"页面
        shiroFilterFactoryBean.setLoginUrl("/admin-ui/login");
        // 登录成功后要跳转的链接
        shiroFilterFactoryBean.setSuccessUrl("/admin-ui/");

        //未授权界面;
        shiroFilterFactoryBean.setUnauthorizedUrl("/admin-ui/login");

        return shiroFilterFactoryBean;
    }

    // 配置加密方式
    // 配置了一下，这货就是验证不过，，改成手动验证算了，以后换加密方式也方便
    @Bean
    public MyCredentialsMatcher myCredentialsMatcher() {
        return new MyCredentialsMatcher();
    }

    // 安全管理器配置
    @Bean
    public DefaultWebSecurityManager defaultWebSecurityManager() {
        DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
        myShiroRealm.setCredentialsMatcher(myCredentialsMatcher());
        securityManager.setRealm(myShiroRealm);
        securityManager.setRememberMeManager(rememberMeManager());
        return securityManager;
    }

    //加入注解的使用，不加入这个注解不生效
    @Bean
    public AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor(DefaultWebSecurityManager defaultWebSecurityManager) {
        AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor = new AuthorizationAttributeSourceAdvisor();
        authorizationAttributeSourceAdvisor.setSecurityManager(defaultWebSecurityManager);
        return authorizationAttributeSourceAdvisor;
    }

    // 配置记住我功能
    @Bean
    @DependsOn("mybatisPlusConfig")
    public SimpleCookie rememberMeCookie() {
        //这个参数是cookie的名称，对应前端的checkbox的name = rememberMe
        SimpleCookie simpleCookie = new SimpleCookie("rememberMe");
        // 记住我cookie生效时间 单位秒
        int adminRememberMeMaxAge = Integer.parseInt(systemConfigService.selectAllConfig().get("admin_remember_me_max_age").toString());
        simpleCookie.setMaxAge(adminRememberMeMaxAge * 24 * 60 * 60);
        return simpleCookie;
    }

    @Bean
    @DependsOn("mybatisPlusConfig")
    public CookieRememberMeManager rememberMeManager() {
        //System.out.println("ShiroConfiguration.rememberMeManager()");
        CookieRememberMeManager cookieRememberMeManager = new CookieRememberMeManager();
        cookieRememberMeManager.setCookie(rememberMeCookie());
        //rememberMe cookie加密的密钥 建议每个项目都不一样 默认AES算法 密钥长度(128 256 512 位)
        cookieRememberMeManager.setCipherKey(Base64.encode("pybbs is the best!".getBytes()));
        return cookieRememberMeManager;
    }

    @Bean
    public FormAuthenticationFilter formAuthenticationFilter() {
        FormAuthenticationFilter formAuthenticationFilter = new FormAuthenticationFilter();
        //对应前端的checkbox的name = rememberMe
        formAuthenticationFilter.setRememberMeParam("rememberMe");
        return formAuthenticationFilter;
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator defaultAAP = new DefaultAdvisorAutoProxyCreator();
        defaultAAP.setProxyTargetClass(true);
        return defaultAAP;
    }

}
