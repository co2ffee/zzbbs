package co.yiiu.pybbs.it;

import org.junit.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 用户端内容必须登录后才能查看（site.login_required=true，也是线上默认值） */
@TestPropertySource(properties = "site.login_required=true")
public class LoginRequiredTest extends AbstractApiTest {

    @Test
    public void anonymous_cannotReadContent() throws Exception {
        for (String url : new String[]{"/api/", "/api/tags", "/api/top100", "/api/search?keyword=a", "/api/topic/1",
                "/api/user/someone", "/api/notification/notRead"}) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        }
    }

    @Test
    public void invalidToken_isRejected() throws Exception {
        mvc.perform(get("/api/tags").header("token", "not-a-real-token")).andExpect(status().isUnauthorized());
    }

    @Test
    public void anonymous_canStillReachLoginEntrances() throws Exception {
        mvc.perform(get("/api/site")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test
    public void loggedInUser_canReadContent() throws Exception {
        String token = loginToken(createUser("reader").getUsername());
        mvc.perform(get("/api/tags").header("token", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/api/").header("token", token)).andExpect(status().isOk());
    }
}
