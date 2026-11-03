package co.yiiu.pybbs.it;

import co.yiiu.pybbs.model.User;
import org.junit.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 用户端的公共接口：站点信息、首页、搜索、积分榜，以及旧页面地址的兼容跳转 */
public class SiteApiTest extends AbstractApiTest {

    @Test
    public void site_returnsPublicConfigOnly() throws Exception {
        mvc.perform(get("/api/site"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.detail.name").exists())
                .andExpect(jsonPath("$.detail.socials").isArray())
                .andExpect(jsonPath("$.detail.smsEnabled").isBoolean())
                // 密钥类配置不能出现在站点信息里
                .andExpect(content().string(not(containsString("sms_secret"))))
                .andExpect(content().string(not(containsString("smtp_password"))))
                .andExpect(content().string(not(containsString("oauth_github_client_secret"))));
    }

    @Test
    public void index_returnsPagedTopics() throws Exception {
        mvc.perform(get("/api/").param("tab", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.detail.records").isArray());
    }

    @Test
    public void top100_exposesOnlyPublicFields() throws Exception {
        User user = createUser("rank");
        mvc.perform(get("/api/top100").param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.detail[?(@.username=='" + user.getUsername() + "')]").exists())
                // 只返回 id/username/avatar/score，不能带邮箱、密码、token
                .andExpect(jsonPath("$.detail[0].email").doesNotExist())
                .andExpect(jsonPath("$.detail[0].password").doesNotExist())
                .andExpect(jsonPath("$.detail[0].token").doesNotExist());
    }

    @Test
    public void search_requiresKeywordAndFindsTopics() throws Exception {
        mvc.perform(get("/api/search").param("keyword", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201));
        mvc.perform(get("/api/search").param("keyword", "nothing-matches-this-keyword"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.detail.records").isArray());
    }

    /** 回归：删掉 Freemarker 后没有 ViewResolver，"redirect:" 曾经导致 500 */
    @Test
    public void legacyAdminLoginPage_redirectsToAdminSpa() throws Exception {
        mvc.perform(get("/adminlogin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin-ui/login"));
    }

    /** 回归：未登录访问 /admin/** 应该是 401 + JSON，而不是 302 到登录页 */
    @Test
    public void adminApi_withoutToken_returns401Json() throws Exception {
        mvc.perform(get("/admin/topic/list"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}
