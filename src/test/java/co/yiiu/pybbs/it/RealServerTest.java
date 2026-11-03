package co.yiiu.pybbs.it;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 启动真实的 Undertow（随机端口）再用 HTTP 请求。MockMvc 不经过 Web 容器，
 * 所以会话跟踪（URL 里追加 ;jsessionid）、Content-Type 这类容器层面的行为只能在这里测。
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class RealServerTest {

    static {
        H2Migrations.prepare();
    }

    @LocalServerPort
    private int port;

    private HttpURLConnection open(String path) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + path).openConnection();
        conn.setInstanceFollowRedirects(false);
        return conn;
    }

    /** 回归：/adminlogin 的 302 的 Location 不能带 ;jsessionid=...（只用 Cookie 跟踪会话） */
    @Test
    public void adminLoginRedirect_hasNoJsessionidInLocation() throws Exception {
        HttpURLConnection conn = open("/adminlogin");
        assertEquals(302, conn.getResponseCode());
        String location = conn.getHeaderField("Location");
        assertNotNull(location);
        assertTrue("跳转目标应该是管理端登录页: " + location, location.endsWith("/admin-ui/login"));
        assertFalse("Location 里不应该有 jsessionid: " + location, location.toLowerCase().contains("jsessionid"));
        // 会话仍然通过 Cookie 下发
        assertNotNull(conn.getHeaderField("Set-Cookie"));
    }

    /** 回归：验证码响应头有明确的 Content-Type 且不缓存 */
    @Test
    public void captcha_hasContentTypeAndNoCache() throws Exception {
        HttpURLConnection conn = open("/common/captcha");
        assertEquals(200, conn.getResponseCode());
        assertEquals("image/gif", conn.getContentType());
        assertTrue(conn.getHeaderField("Cache-Control").contains("no-store"));
    }

    /** 回归：未登录访问 /admin/** 返回 401 JSON，而不是 302 */
    @Test
    public void adminApi_withoutToken_is401() throws Exception {
        HttpURLConnection conn = open("/admin/topic/list");
        conn.setRequestProperty("Accept", "application/json");
        assertEquals(401, conn.getResponseCode());
        assertTrue(conn.getContentType().contains("json"));
    }
}
