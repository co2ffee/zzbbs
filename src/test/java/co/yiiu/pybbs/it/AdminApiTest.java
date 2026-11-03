package co.yiiu.pybbs.it;

import co.yiiu.pybbs.model.AdminUser;
import co.yiiu.pybbs.model.Role;
import co.yiiu.pybbs.model.Tag;
import co.yiiu.pybbs.model.Topic;
import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.service.IAdminUserService;
import co.yiiu.pybbs.service.IPermissionService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import javax.sql.DataSource;
import co.yiiu.pybbs.service.IRoleService;
import co.yiiu.pybbs.service.ITagService;
import co.yiiu.pybbs.service.ITopicService;
import co.yiiu.pybbs.util.bcrypt.BCryptPasswordEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
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
    private IPermissionService permissionService;
    @Autowired
    private RequestMappingHandlerMapping handlerMapping;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private ITopicService topicService;
    @Autowired
    private ITagService tagService;

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

    /**
     * 回归（越权）：一个没有分配任何权限的角色，查出来的权限必须是空的。
     * 原来用 in(权限id列表) 查询，列表为空时条件失效，会查出所有权限，等于“零权限角色 = 超级管理员”。
     */
    @Test
    public void roleWithoutPermissions_hasNoPermissionsAtAll() {
        String roleName = unique("emptyrole");
        roleService.insert(roleName, new Integer[0]);
        Integer roleId = null;
        for (Role role : roleService.selectAll()) if (roleName.equals(role.getName())) roleId = role.getId();
        assertTrue("角色应该创建成功", roleId != null);
        assertEquals("没有权限的角色不应该查出任何权限", 0, permissionService.selectByRoleId(roleId).size());
        // 对比：超级管理员（角色 1）确实有很多权限
        assertTrue(permissionService.selectByRoleId(1).size() > 10);
    }

    /** 回归：仪表盘“今日新增”要能统计到今天刚新增的数据（原来按 MySQL 的 curdate() 算，时区不一致时全是 0） */
    @Test
    public void dashboard_countsContentCreatedToday() throws Exception {
        String token = adminToken();
        String before = mvc.perform(withToken(get("/admin/index"), token)).andReturn().getResponse().getContentAsString();
        int topicsBefore = readDetail(before).path("topic_count").asInt();
        int usersBefore = readDetail(before).path("user_count").asInt();

        User author = createUser("today");
        topicService.insert(unique("todaytopic"), "content", "", author);

        String after = mvc.perform(withToken(get("/admin/index"), token)).andReturn().getResponse().getContentAsString();
        assertEquals(topicsBefore + 1, readDetail(after).path("topic_count").asInt());
        assertEquals(usersBefore + 1, readDetail(after).path("user_count").asInt());
    }

    /** 回归：读取存储桶文件列表失败时要告诉管理员原因，而不是返回空列表（页面显示“0 个文件”，看不出出错了） */
    @Test
    public void fileList_reportsStorageErrorInsteadOfEmptyList() throws Exception {
        // 测试环境没有配置 Backblaze 密钥，读取一定会失败
        mvc.perform(withToken(get("/admin/file/list"), adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.description").value(containsString("读取存储桶文件列表失败")));
    }

    /**
     * 回归：编辑标签。原来不管有没有选图标都在服务端调 upload，AWS 上传插件会返回一整串预签名 JSON 当成 icon 写库，
     * 超出列长度导致保存必失败。现在不传 icon 就保持原样，传了只接受自己存储域名下的地址。
     */
    @Test
    public void tagEdit_keepsIconWhenOmittedAndOnlyAcceptsOwnStorageUrls() throws Exception {
        Tag tag = tagService.insertTag(unique("tagicon")).get(0);
        String token = adminToken();
        String id = tag.getId().toString();
        String ok = "http://localhost:8080/static/upload/tag/new-icon.png";

        // 不传 icon：保存成功，图标保持不变（原来这里必失败）
        mvc.perform(withToken(post("/admin/tag/edit"), token).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("id", id).param("name", tag.getName()).param("description", "d1").param("topicCount", "0"))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(withToken(get("/admin/tag/edit").param("id", id), token))
                .andExpect(jsonPath("$.detail.description").value("d1"))
                .andExpect(jsonPath("$.detail.icon").doesNotExist());

        // 传自己存储域名下的地址：保存
        mvc.perform(withToken(post("/admin/tag/edit"), token).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("id", id).param("name", tag.getName()).param("description", "d2").param("topicCount", "0").param("icon", ok))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(withToken(get("/admin/tag/edit").param("id", id), token))
                .andExpect(jsonPath("$.detail.icon").value(ok));

        // 再次不传 icon：图标保持
        mvc.perform(withToken(post("/admin/tag/edit"), token).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("id", id).param("name", tag.getName()).param("description", "d3").param("topicCount", "0"))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(withToken(get("/admin/tag/edit").param("id", id), token))
                .andExpect(jsonPath("$.detail.icon").value(ok));

        // 外部地址、超长内容拒绝
        mvc.perform(withToken(post("/admin/tag/edit"), token).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("id", id).param("name", tag.getName()).param("description", "d4").param("topicCount", "0")
                .param("icon", "https://evil.example.com/x.png"))
                .andExpect(jsonPath("$.code").value(201));
    }

    /** 上传地址接口：需要登录；存储桶没配置时返回明确的提示，而不是“服务器出错啦” */
    @Test
    public void tagUploadUrl_requiresLoginAndReportsMissingStorageConfig() throws Exception {
        mvc.perform(get("/admin/tag/upload_url")).andExpect(status().isUnauthorized());
        mvc.perform(withToken(get("/admin/tag/upload_url"), adminToken()))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.description").value(containsString("获取上传地址失败")));
    }

    /** 回归：日期筛选只填一端也要生效；开始和结束填同一天要包含这一天 */
    @Test
    public void topicAndCommentList_dateFilters() throws Exception {
        User author = createUser("datefilter");
        String authorToken = loginToken(author.getUsername());
        String title = unique("datetopic");
        Topic topic = topicService.insert(title, "content", "", author);
        mvc.perform(withToken(post("/api/comment"), authorToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"date filter comment\",\"topicId\":\"" + topic.getId() + "\"}"))
                .andExpect(jsonPath("$.code").value(200));

        String token = adminToken();
        String today = LocalDate.now().toString();
        String tomorrow = LocalDate.now().plusDays(1).toString();
        String yesterday = LocalDate.now().minusDays(1).toString();

        for (String path : new String[]{"/admin/topic/list", "/admin/comment/list"}) {
            java.util.function.BiConsumer<String, String> expect = (query, count) -> {
                try {
                    String url = path + "?username=" + author.getUsername() + query;
                    String body = mvc.perform(withToken(get(url), token)).andReturn().getResponse().getContentAsString();
                    assertEquals(path + query + " -> " + body, Integer.parseInt(count), readDetail(body).path("records").size());
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            };
            expect.accept("", "1");                                                      // 不筛选
            expect.accept("&startDate=" + today, "1");                                   // 只填开始日期：以前被忽略
            expect.accept("&endDate=" + today, "1");                                     // 只填结束日期，包含当天
            expect.accept("&startDate=" + today + "&endDate=" + today, "1");             // 同一天：以前是 No rows
            expect.accept("&startDate=" + tomorrow, "0");
            expect.accept("&endDate=" + yesterday, "0");
            expect.accept("&startDate=" + yesterday + "&endDate=" + tomorrow, "1");
            mvc.perform(withToken(get(path).param("startDate", "not-a-date"), token))
                    .andExpect(jsonPath("$.code").value(201))
                    .andExpect(jsonPath("$.description").value(containsString("日期格式")));
        }
    }

    /**
     * 回归：代码里每个 @RequiresPermissions 要求的权限，权限表里都必须存在。
     * 原来缺了 role:add 和 admin_user:delete，连超级管理员都无法新增角色、删除后台用户。
     */
    @Test
    public void everyRequiredPermissionExistsInPermissionTable() {
        java.util.Set<String> required = new java.util.TreeSet<>();
        for (HandlerMethod method : handlerMapping.getHandlerMethods().values()) {
            RequiresPermissions annotation = method.getMethodAnnotation(RequiresPermissions.class);
            if (annotation != null) required.addAll(java.util.Arrays.asList(annotation.value()));
        }
        assertTrue("应该能扫描到后台接口上的权限要求", required.size() > 20);
        java.util.Set<String> existing = new java.util.HashSet<>(
                new JdbcTemplate(dataSource).queryForList("select `value` from permission", String.class));
        required.removeAll(existing);
        assertTrue("这些权限代码里要求了，但权限表里没有: " + required, required.isEmpty());
    }

    /** 超级管理员可以新增角色、删除后台用户（这两个权限原来不存在） */
    @Test
    public void superAdmin_canAddRolesAndDeleteAdminUsers() throws Exception {
        String token = adminToken();
        mvc.perform(withToken(get("/admin/role/add"), token)).andExpect(jsonPath("$.code").value(200));

        String username = unique("todelete");
        AdminUser adminUser = new AdminUser();
        adminUser.setUsername(username);
        adminUser.setPassword(new BCryptPasswordEncoder().encode(PASSWORD));
        adminUser.setInTime(new Date());
        adminUser.setRoleId(2);
        adminUserService.insert(adminUser);
        Integer id = adminUserService.selectByUsername(username).getId();

        mvc.perform(withToken(get("/admin/admin_user/delete").param("id", id.toString()), token))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(null, adminUserService.selectByUsername(username));
    }
}
