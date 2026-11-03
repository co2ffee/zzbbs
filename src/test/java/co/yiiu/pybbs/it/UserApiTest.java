package co.yiiu.pybbs.it;

import co.yiiu.pybbs.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 用户端接口：登录（验证码在测试里 mock）、设置、话题、评论、激活 */
public class UserApiTest extends AbstractApiTest {

    private String login(String username, String password, String captcha) throws Exception {
        return mvc.perform(post("/api/login").sessionAttr("_captcha", CAPTCHA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"captcha\":\"" + captcha + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    public void login_returnsTokenAndNeverLeaksSecrets() throws Exception {
        User user = createUser("login");
        String body = login(user.getUsername(), PASSWORD, CAPTCHA);
        JsonNode detail = readDetail(body);
        assertEquals(200, json.readTree(body).path("code").asInt());
        assertTrue("应该返回 token", detail.path("token").asText().length() > 10);
        // User 对象里的 password / token 都带了 @JsonIgnore，不能随登录结果泄露
        assertTrue(detail.path("user").path("password").isMissingNode());
        assertTrue(detail.path("user").path("token").isMissingNode());
    }

    @Test
    public void login_rejectsWrongCaptchaAndWrongPassword() throws Exception {
        User user = createUser("badlogin");
        assertEquals(201, json.readTree(login(user.getUsername(), PASSWORD, "zzzz")).path("code").asInt());
        assertEquals(201, json.readTree(login(user.getUsername(), "wrong-password", CAPTCHA)).path("code").asInt());
        assertEquals(201, json.readTree(login("no_such_user_x", PASSWORD, CAPTCHA)).path("code").asInt());
    }

    @Test
    public void login_withoutCaptchaInSession_isRejected() throws Exception {
        User user = createUser("nocap");
        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user.getUsername() + "\",\"password\":\"" + PASSWORD + "\",\"captcha\":\"abcd\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201));
    }

    @Test
    public void settings_requiresToken_thenReturnsCurrentUser() throws Exception {
        User user = createUser("settings");
        mvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201));

        String token = loginToken(user.getUsername());
        mvc.perform(withToken(get("/api/settings"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.detail.username").value(user.getUsername()))
                .andExpect(jsonPath("$.detail.password").doesNotExist())
                .andExpect(jsonPath("$.detail.token").doesNotExist());

        mvc.perform(withToken(put("/api/settings"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bio\":\"hello bio\",\"website\":\"http://example.com\",\"telegramName\":\"\",\"emailNotification\":\"true\"}"))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(withToken(get("/api/settings"), token))
                .andExpect(jsonPath("$.detail.bio").value("hello bio"));
    }

    @Test
    public void topicAndCommentFlow() throws Exception {
        User author = createUser("author");
        User other = createUser("other");
        String authorToken = loginToken(author.getUsername());
        String otherToken = loginToken(other.getUsername());
        String title = unique("topic");

        // 发话题（带标签）
        String created = mvc.perform(withToken(post("/api/topic"), authorToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"content\":\"hello **world**\",\"tag\":\"java\"}"))
                .andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString();
        int topicId = readDetail(created).path("id").asInt();
        assertTrue(topicId > 0);

        // 再发一个，用来验证详情里的“作者的其他话题”
        mvc.perform(withToken(post("/api/topic"), authorToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + unique("second") + "\",\"content\":\"another\",\"tag\":\"java\"}"))
                .andExpect(jsonPath("$.code").value(200));

        // 详情：不泄露作者的 token/password，包含 otherTopics
        mvc.perform(get("/api/topic/" + topicId))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.detail.topic.title").value(title))
                .andExpect(jsonPath("$.detail.topicUser.username").value(author.getUsername()))
                .andExpect(jsonPath("$.detail.topicUser.token").doesNotExist())
                .andExpect(jsonPath("$.detail.topicUser.password").doesNotExist())
                .andExpect(jsonPath("$.detail.otherTopics").isArray())
                .andExpect(jsonPath("$.detail.tags[0].name").value("java"));

        // 搜索能找到这个话题
        mvc.perform(get("/api/search").param("keyword", title))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.detail.records[0].title").value(title));

        // 评论
        String commented = mvc.perform(withToken(post("/api/comment"), otherToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"nice post\",\"topicId\":\"" + topicId + "\"}"))
                .andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString();
        int commentId = readDetail(commented).path("id").asInt();

        // 取单条评论：只有作者本人可以（评论编辑页使用）
        mvc.perform(withToken(get("/api/comment/" + commentId), otherToken))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.detail.comment.content").value("nice post"));
        mvc.perform(withToken(get("/api/comment/" + commentId), authorToken))
                .andExpect(jsonPath("$.code").value(201));
        mvc.perform(get("/api/comment/" + commentId))
                .andExpect(jsonPath("$.code").value(201));

        // 评论作者修改评论
        mvc.perform(withToken(put("/api/comment/" + commentId), otherToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"edited\"}"))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(withToken(get("/api/comment/" + commentId), otherToken))
                .andExpect(jsonPath("$.detail.comment.content").value("edited"));

        // 话题详情里的评论列表
        mvc.perform(get("/api/topic/" + topicId))
                .andExpect(jsonPath("$.detail.comments").isArray());

        // 收藏 / 取消收藏
        mvc.perform(withToken(post("/api/collect/" + topicId), otherToken)).andExpect(jsonPath("$.code").value(200));
        mvc.perform(withToken(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/collect/" + topicId), otherToken))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    public void inactiveUser_cannotPublishTopics() throws Exception {
        String username = unique("inactive");
        userService.addUser(username, PASSWORD, "http://localhost/a.png", username + "@example.com", null, null, false);
        String token = loginToken(username);
        mvc.perform(withToken(post("/api/topic"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + unique("t") + "\",\"content\":\"x\",\"tag\":\"\"}"))
                .andExpect(jsonPath("$.code").value(201));
    }

    @Test
    public void active_withInvalidCode_isRejected() throws Exception {
        User user = createUser("act");
        mvc.perform(get("/api/active").param("email", user.getEmail()).param("code", "not-a-real-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201));
        mvc.perform(get("/api/active"))
                .andExpect(jsonPath("$.code").value(201));
    }

    @Test
    public void profile_doesNotLeakToken() throws Exception {
        User user = createUser("prof");
        mvc.perform(get("/api/user/" + user.getUsername()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.detail.user.username").value(user.getUsername()))
                .andExpect(content().string(not(containsString("\"token\""))))
                .andExpect(content().string(not(containsString("\"password\""))));
    }

    /** 点赞是切换式：点一次点赞，再点一次取消，详情里的点赞人数应该回到 0，并且服务端返回最新计数 */
    @Test
    public void topicVote_togglesAndCountReturnsToZero() throws Exception {
        User author = createUser("voteauthor");
        User voter = createUser("voter");
        String authorToken = loginToken(author.getUsername());
        String voterToken = loginToken(voter.getUsername());
        String created = mvc.perform(withToken(post("/api/topic"), authorToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + unique("votetopic") + "\",\"content\":\"vote me\",\"tag\":\"\"}"))
                .andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString();
        int topicId = readDetail(created).path("id").asInt();

        // 不能给自己点赞
        mvc.perform(withToken(get("/api/topic/" + topicId + "/vote"), authorToken)).andExpect(jsonPath("$.code").value(201));

        // 点赞：返回 1，详情里的 upIds 包含这个用户
        mvc.perform(withToken(get("/api/topic/" + topicId + "/vote"), voterToken))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.detail").value(1));
        mvc.perform(get("/api/topic/" + topicId))
                .andExpect(jsonPath("$.detail.topic.upIds").value(voter.getId().toString()));

        // 取消点赞：返回 0，详情里的 upIds 为空
        mvc.perform(withToken(get("/api/topic/" + topicId + "/vote"), voterToken))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.detail").value(0));
        String after = mvc.perform(get("/api/topic/" + topicId)).andReturn().getResponse().getContentAsString();
        String upIds = readDetail(after).path("topic").path("upIds").asText("");
        assertEquals("取消点赞后 upIds 应该为空，实际: [" + upIds + "]", "", upIds);
    }

    /** 打开详情页计一次浏览；点赞/评论后的“刷新”带 countView=false，不重复计数 */
    @Test
    public void topicDetail_countsViewsOnlyWhenAsked() throws Exception {
        User author = createUser("viewauthor");
        String token = loginToken(author.getUsername());
        String created = mvc.perform(withToken(post("/api/topic"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + unique("viewtopic") + "\",\"content\":\"views\",\"tag\":\"\"}"))
                .andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString();
        int topicId = readDetail(created).path("id").asInt();
        int start = readDetail(created).path("view").asInt();
        assertEquals("新话题的浏览数从 0 开始", 0, start);

        mvc.perform(get("/api/topic/" + topicId)).andExpect(jsonPath("$.detail.topic.view").value(start + 1));
        mvc.perform(get("/api/topic/" + topicId).param("countView", "false"))
                .andExpect(jsonPath("$.detail.topic.view").value(start + 1));
        mvc.perform(get("/api/topic/" + topicId).param("countView", "false"))
                .andExpect(jsonPath("$.detail.topic.view").value(start + 1));
        mvc.perform(get("/api/topic/" + topicId)).andExpect(jsonPath("$.detail.topic.view").value(start + 2));
    }

    /** 访问不存在的资源，要返回明确的提示，而不是笼统的“服务器出错啦” */
    @Test
    public void missingResources_returnClearMessages() throws Exception {
        mvc.perform(get("/api/topic/99999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.description").value("话题不存在"));
        // 非法参数（id 不是数字）
        mvc.perform(get("/api/topic/abc"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.description").value("请求参数不正确"));
        for (String path : new String[]{"", "/topics", "/comments", "/collects"}) {
            mvc.perform(get("/api/user/no_such_user_zz" + path))
                    .andExpect(jsonPath("$.code").value(201))
                    .andExpect(jsonPath("$.description").value("用户不存在"));
        }
        // 已有的行为保持不变：标签不存在
        mvc.perform(get("/api/tag/no-such-tag-zz")).andExpect(jsonPath("$.code").value(201));
    }

    /** 头像：只接受自己存储域名下的地址，保存后用户资料里能读到 */
    @Test
    public void settings_avatarOnlyAcceptsOwnStorageUrls() throws Exception {
        User user = createUser("avatar");
        String token = loginToken(user.getUsername());
        String ok = "http://localhost:8080/static/upload/avatar/" + user.getUsername() + "/new.png";

        mvc.perform(withToken(put("/api/settings"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bio\":\"b\",\"avatar\":\"" + ok + "\"}"))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(withToken(get("/api/settings"), token)).andExpect(jsonPath("$.detail.avatar").value(ok));

        // 外部地址、以及“前缀看起来像但域名不同”的地址都要拒绝
        for (String bad : new String[]{"https://evil.example.com/a.png", "http://localhost:8080/static/upload.evil.com/a.png",
                "javascript:alert(1)"}) {
            mvc.perform(withToken(put("/api/settings"), token).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"bio\":\"b\",\"avatar\":\"" + bad + "\"}"))
                    .andExpect(jsonPath("$.code").value(201));
        }
        // 不传 avatar：保持原来的头像不变
        mvc.perform(withToken(put("/api/settings"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bio\":\"only bio\"}")).andExpect(jsonPath("$.code").value(200));
        mvc.perform(withToken(get("/api/settings"), token)).andExpect(jsonPath("$.detail.avatar").value(ok));
    }
}
