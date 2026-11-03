package co.yiiu.pybbs.it;

import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.service.IUserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 后端集成测试的公共基类：
 * - H2 内存库（MySQL 兼容模式），迁移脚本由 {@link H2Migrations} 自动转换，不连任何真实数据库；
 * - MockMvc 直接调用 controller，验证码在测试里直接往 session 写入 {@value #CAPTCHA}，不走真实的验证码接口；
 * - 同一个 Spring 上下文被所有测试类共享，数据库里的数据会累积，所以每个测试都用唯一的用户名/标题。
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractApiTest {

    static {
        // 必须在 Spring 上下文（Flyway）启动之前完成
        H2Migrations.prepare();
    }

    /** 测试里用的“验证码”，通过 session 属性 _captcha 注入 */
    protected static final String CAPTCHA = "abcd";
    // 测试用户的密码每次运行随机生成，源码里不放密码字面量（避免被密钥扫描当成硬编码密码）
    protected static final String PASSWORD = "Pw" + java.util.UUID.randomUUID().toString().replace("-", "");
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected IUserService userService;
    protected final ObjectMapper json = new ObjectMapper();

    protected static String unique(String prefix) {
        return prefix + SEQ.incrementAndGet() + Long.toString(System.nanoTime() % 100000, 36);
    }

    /** 直接在库里建一个已激活的普通用户（不发激活邮件、不生成头像文件） */
    protected User createUser(String prefix) {
        String username = unique(prefix);
        User user = userService.addUser(username, PASSWORD, "http://localhost/avatar.png", username + "@example.com",
                null, null, false);
        user.setActive(true);
        userService.update(user);
        return userService.selectByUsername(username);
    }

    /** 通过登录接口拿 token（验证码用 session 属性 mock） */
    protected String loginToken(String username) throws Exception {
        String body = mvc.perform(post("/api/login").sessionAttr("_captcha", CAPTCHA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"captcha\":\"" + CAPTCHA + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode root = json.readTree(body);
        if (root.path("code").asInt() != 200) throw new AssertionError("登录失败: " + body);
        return root.path("detail").path("token").asText();
    }

    protected static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("token", token);
    }

    protected JsonNode readDetail(String body) throws Exception {
        return json.readTree(body).path("detail");
    }
}
