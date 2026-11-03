package co.yiiu.pybbs.it;

import co.yiiu.pybbs.model.AdminUser;
import co.yiiu.pybbs.model.Role;
import co.yiiu.pybbs.model.Topic;
import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.service.IAdminUserService;
import co.yiiu.pybbs.service.IRoleService;
import co.yiiu.pybbs.service.ITopicService;
import co.yiiu.pybbs.util.bcrypt.BCryptPasswordEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Date;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 管理端接口：token 登录（验证码 mock）、权限、未登录的 401、各列表接口的 JSON 结构 */
public class AdminApiTest extends AbstractApiTest {

    // 迁移脚本里内置的超级管理员：admin / 123123
    private static final String SEED_ADMIN = "admin";
    private static final String SEED_PASSWORD = "123123";

    @Autowired
    private IAdminUserService adminUserService;
    @Autowired
    private IRoleService roleService;
    @Autowired
    private ITopicService topicService;

    private String adminLogin(String username, String password, String captcha) throws Exception {
        return mvc.perform(post("/admin/api/login").sessionAttr("_captcha", CAPTCHA)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", username).param("password", password).param("code", captcha))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String adminToken() throws Exception {
        String body = adminLogin(SEED_ADMIN, SEED_PASSWORD, CAPTCHA);
        assertEquals(body, 200, json.readTree(body).path("code").asInt());
        return readDetail(body).path("token").asText();
    }

    @Test
    public void login_returnsTokenAndNeverLeaksPasswordHash() throws Exception {
        String body = adminLogin(SEED_ADMIN, SEED_PASSWORD, CAPTCHA);
        JsonNode detail = readDetail(body);
        assertEquals(200, json.readTree(body).path("code").asInt());
        assertTrue(detail.path("token").asText().length() > 10);
        // AdminUser 的 password / token 都带了 @JsonIgnore
        assertTrue(detail.path("adminUser").path("password").isMissingNode());
        assertTrue(detail.path("adminUser").path("token").isMissingNode());
        assertEquals(SEED_ADMIN, detail.path("adminUser").path("username").asText());
        // 再登录一次拿到的是同一个 token（永久有效，手动刷新）
        assertEquals(detail.path("token").asText(), readDetail(adminLogin(SEED_ADMIN, SEED_PASSWORD, CAPTCHA)).path("token").asText());
    }

    @Test
    public void login_rejectsWrongCaptchaPasswordAndUnknownUser() throws Exception {
        assertEquals(201, json.readTree(adminLogin(SEED_ADMIN, SEED_PASSWORD, "zzzz")).path("code").asInt());
        assertEquals(201, json.readTree(adminLogin(SEED_ADMIN, "wrong", CAPTCHA)).path("code").asInt());
        assertEquals(201, json.readTree(adminLogin("nobody", SEED_PASSWORD, CAPTCHA)).path("code").asInt());
        // session 里没有验证码
        mvc.perform(post("/admin/api/login").param("username", SEED_ADMIN).param("password", SEED_PASSWORD).param("code", CAPTCHA))
                .andExpect(jsonPath("$.code").value(201));
    }

    @Test
    public void protectedApi_withoutOrWithBadToken_returns401() throws Exception {
        mvc.perform(get("/admin/topic/list")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        mvc.perform(withToken(get("/admin/topic/list"), "definitely-not-a-valid-token"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }

    @Test
    public void dashboard_withToken_returnsStats() throws Exception {
        mvc.perform(withToken(get("/admin/index"), adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.detail.topic_count").exists())
                .andExpect(jsonPath("$.detail.user_count").exists())
                .andExpect(jsonPath("$.detail.totalMemorySize").exists());
    }

    @Test
    public void listEndpoints_returnJson() throws Exception {
        String token = adminToken();
        for (String path : new String[]{"/admin/topic/list", "/admin/comment/list", "/admin/tag/list", "/admin/user/list",
                "/admin/sensitive_word/list"}) {
            mvc.perform(withToken(get(path), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.detail.records").isArray());
        }
        for (String path : new String[]{"/admin/role/list", "/admin/admin_user/list"}) {
            mvc.perform(withToken(get(path), token))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.detail").isArray());
        }
        mvc.perform(withToken(get("/admin/permission/list"), token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.detail.permissions").isArray())
                .andExpect(jsonPath("$.detail.childPermissions").isArray());
        mvc.perform(withToken(get("/admin/file/list"), token)).andExpect(status().isOk());
    }

    /** 回归：admin_user/list 曾经用 select u.*，把密码哈希和 token 一起返回 */
    @Test
    public void adminUserList_doesNotLeakPasswordHashOrToken() throws Exception {
        adminToken(); // 确保种子管理员已经有 token
        mvc.perform(withToken(get("/admin/admin_user/list"), adminToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.detail[0].username").exists())
                .andExpect(jsonPath("$.detail[0].password").doesNotExist())
                .andExpect(jsonPath("$.detail[0].token").doesNotExist())
                .andExpect(content().string(not(containsString("$2a$"))));
    }

    @Test
    public void systemConfig_isGroupedByCategory() throws Exception {
        String body = mvc.perform(withToken(get("/admin/system/edit"), adminToken()))
                .andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString();
        JsonNode groups = readDetail(body);
        assertTrue("系统设置应按分类分组返回", groups.size() > 3);
        JsonNode firstItem = groups.elements().next().get(0);
        assertTrue(firstItem.has("key") && firstItem.has("value") && firstItem.has("type"));
    }

    /** 带 token 的请求也要走 @RequiresPermissions：没有对应权限的角色不能改系统设置 */
    @Test
    public void adminWithoutPermission_isForbidden() throws Exception {
        String roleName = unique("noperm");
        roleService.insert(roleName, new Integer[0]);
        Integer roleId = null;
        for (Role role : roleService.selectAll()) if (roleName.equals(role.getName())) roleId = role.getId();
        assertTrue("角色应该创建成功", roleId != null);

        String username = unique("adm");
        AdminUser adminUser = new AdminUser();
        adminUser.setUsername(username);
        adminUser.setPassword(new BCryptPasswordEncoder().encode(PASSWORD));
        adminUser.setInTime(new Date());
        adminUser.setRoleId(roleId);
        adminUserService.insert(adminUser);

        String token = readDetail(adminLogin(username, PASSWORD, CAPTCHA)).path("token").asText();
        assertTrue(token.length() > 10);

        // 已登录，但没有 system:edit 权限
        mvc.perform(withToken(get("/admin/system/edit"), token))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.description").value(containsString("权限")));
        // 超级管理员可以
        mvc.perform(withToken(get("/admin/system/edit"), adminToken()))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    public void topicModeration_goodTopAndDelete() throws Exception {
        User author = createUser("moderated");
        String title = unique("modtopic");
        Topic topic = topicService.insert(title, "content", "", author);
        String token = adminToken();

        mvc.perform(withToken(get("/admin/topic/list").param("username", author.getUsername()), token))
                .andExpect(jsonPath("$.detail.records[0].title").value(title));

        mvc.perform(withToken(get("/admin/topic/good").param("id", topic.getId().toString()), token))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(withToken(get("/admin/topic/top").param("id", topic.getId().toString()), token))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(withToken(get("/admin/topic/edit").param("id", topic.getId().toString()), token))
                .andExpect(jsonPath("$.detail.topic.good").value(true))
                .andExpect(jsonPath("$.detail.topic.top").value(true))
                .andExpect(jsonPath("$.detail.tags").value(""));

        // 编辑表单是 application/x-www-form-urlencoded
        mvc.perform(withToken(post("/admin/topic/edit"), token).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("id", topic.getId().toString()).param("title", title + "-edited").param("content", "new content")
                .param("tags", "java"))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(withToken(get("/admin/topic/edit").param("id", topic.getId().toString()), token))
                .andExpect(jsonPath("$.detail.topic.title").value(title + "-edited"))
                .andExpect(jsonPath("$.detail.tags").value("java"));

        // 删除后，用户端详情查不到（逻辑删除）
        mvc.perform(withToken(get("/admin/topic/delete").param("id", topic.getId().toString()), token))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(withToken(get("/admin/topic/edit").param("id", topic.getId().toString()), token))
                .andExpect(jsonPath("$.detail.topic.deleted").value(1));
    }
}
