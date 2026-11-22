package co.yiiu.pybbs.it;

import co.yiiu.pybbs.model.AdminUser;
import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.service.IAdminUserService;
import co.yiiu.pybbs.util.bcrypt.BCryptPasswordEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 用户集市：用户自己上架、定价，买家付积分，积分转给卖家；内容付款后才给 */
public class MarketApiTest extends AbstractApiTest {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private IAdminUserService adminUserService;
    private String adminToken;

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    private String admin() throws Exception {
        if (adminToken == null) {
            String username = unique("mkadmin");
            AdminUser adminUser = new AdminUser();
            adminUser.setUsername(username);
            adminUser.setPassword(new BCryptPasswordEncoder().encode(PASSWORD));
            adminUser.setInTime(new Date());
            adminUser.setRoleId(1);
            adminUserService.insert(adminUser);
            String body = mvc.perform(post("/admin/api/login").sessionAttr("_captcha", CAPTCHA)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("username", username).param("password", PASSWORD).param("code", CAPTCHA))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            adminToken = json.readTree(body).path("detail").path("token").asText();
        }
        return adminToken;
    }

    private JsonNode call(MockHttpServletRequestBuilder b) throws Exception {
        return json.readTree(mvc.perform(b).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
    }

    private User userWithScore(String prefix, int score) {
        User u = createUser(prefix);
        jdbc().update("update user set score = ? where id = ?", score, u.getId());
        return u;
    }

    private int scoreOf(User u) {
        return jdbc().queryForObject("select score from user where id = ?", Integer.class, u.getId());
    }

    /** 上架一件带文本内容的商品，返回商品 id */
    private int list(String token, String name, int score, int stock, String text) throws Exception {
        JsonNode r = call(withToken(post("/api/market/item"), token).param("name", name)
                .param("score", String.valueOf(score)).param("stock", String.valueOf(stock)).param("rewardText", text));
        assertEquals(r.toString(), 200, r.path("code").asInt());
        return r.path("detail").path("id").asInt();
    }

    private JsonNode buy(String token, int itemId) throws Exception {
        return call(withToken(post("/api/market/item/" + itemId + "/buy"), token));
    }

    @Test
    public void buy_movesScoreFromBuyerToSeller_andGivesContentOnlyAfterPayment() throws Exception {
        User seller = userWithScore("mkseller", 10);
        User buyer = userWithScore("mkbuyer", 100);
        String sToken = loginToken(seller.getUsername());
        String bToken = loginToken(buyer.getUsername());
        int id = list(sToken, "我的笔记", 30, -1, "密码是 abc123");

        // 列表里买家看不到内容，只知道会得到文本
        JsonNode items = call(withToken(get("/api/market/items"), bToken)).path("detail").path("items");
        JsonNode mine = null;
        for (JsonNode i : items) if (i.path("id").asInt() == id) mine = i;
        assertTrue("商品在列表里", mine != null);
        assertEquals("text", mine.path("delivers").get(0).asText());
        assertFalse("列表不泄露内容", mine.has("reward") || mine.toString().contains("abc123"));
        assertEquals(seller.getUsername(), mine.path("sellerName").asText());

        JsonNode r = buy(bToken, id);
        assertEquals(r.toString(), 200, r.path("code").asInt());
        assertEquals("密码是 abc123", r.path("detail").path("reward").path("text").asText());
        assertEquals(70, r.path("detail").path("totalScore").asInt());
        assertEquals("买家扣 30", 70, scoreOf(buyer));
        assertEquals("卖家收 30（原来 10）", 40, scoreOf(seller));

        // 卖家改价、改内容、下架都不影响已经买到的人（快照）；买家的订单里能再看到
        call(withToken(post("/api/market/item"), sToken).param("id", String.valueOf(id)).param("name", "改了")
                .param("score", "999").param("rewardText", "新内容"));
        call(withToken(post("/api/market/item/" + id + "/off"), sToken));
        JsonNode orders = call(withToken(get("/api/market/orders"), bToken)).path("detail");
        assertEquals(1, orders.size());
        assertEquals("我的笔记", orders.get(0).path("itemName").asText());
        assertEquals(30, orders.get(0).path("score").asInt());
        assertEquals("密码是 abc123", orders.get(0).path("reward").path("text").asText());

        // 卖家的销售记录：买家名和收入，不含内容
        JsonNode sales = call(withToken(get("/api/market/sales"), sToken)).path("detail");
        assertEquals(1, sales.size());
        assertEquals(30, sales.get(0).path("income").asInt());
        assertEquals(buyer.getUsername(), sales.get(0).path("buyerName").asText());
        assertFalse(sales.get(0).toString().contains("abc123"));
    }

    @Test
    public void cannotBuyOwnItem_orWithoutEnoughScore_orWhenOffShelf() throws Exception {
        User seller = userWithScore("mkself", 100);
        User poor = userWithScore("mkpoor", 5);
        User buyer = userWithScore("mkok", 100);
        String sToken = loginToken(seller.getUsername());
        int id = list(sToken, "贵的东西", 50, -1, "内容");

        assertTrue("不能买自己的", buy(sToken, id).path("code").asInt() != 200);
        assertEquals(100, scoreOf(seller));
        JsonNode poorRes = buy(loginToken(poor.getUsername()), id);
        assertTrue("积分不够", poorRes.path("code").asInt() != 200);
        assertEquals(5, scoreOf(poor));
        assertEquals("没成交，卖家积分不变", 100, scoreOf(seller));

        call(withToken(post("/api/market/item/" + id + "/off"), sToken));
        assertTrue("下架后买不了", buy(loginToken(buyer.getUsername()), id).path("code").asInt() != 200);
        assertEquals(100, scoreOf(buyer));
        call(withToken(post("/api/market/item/" + id + "/on"), sToken));
        assertEquals(200, buy(loginToken(buyer.getUsername()), id).path("code").asInt());
    }

    @Test
    public void validation_andOwnership() throws Exception {
        User a = userWithScore("mka", 0);
        User b = userWithScore("mkb", 0);
        String aToken = loginToken(a.getUsername());
        String bToken = loginToken(b.getUsername());

        // 没有内容 / 价格不对 / 外部文件地址 都被拒绝
        assertTrue(call(withToken(post("/api/market/item"), aToken).param("name", "空").param("score", "10"))
                .path("code").asInt() != 200);
        assertTrue(call(withToken(post("/api/market/item"), aToken).param("name", "零").param("score", "0")
                .param("rewardText", "x")).path("code").asInt() != 200);
        assertTrue(call(withToken(post("/api/market/item"), aToken).param("name", "外链").param("score", "5")
                .param("rewardUrl", "http://evil.example.com/a.mp4").param("rewardType", "video"))
                .path("code").asInt() != 200);

        int id = list(aToken, "A 的商品", 10, 3, "内容");
        // 别人不能改、不能下架别人的商品
        assertTrue(call(withToken(post("/api/market/item"), bToken).param("id", String.valueOf(id)).param("name", "抢")
                .param("score", "1").param("rewardText", "x")).path("code").asInt() != 200);
        assertTrue(call(withToken(post("/api/market/item/" + id + "/off"), bToken)).path("code").asInt() != 200);
        // 不能把唯一的内容清空
        assertTrue(call(withToken(post("/api/market/item"), aToken).param("id", String.valueOf(id)).param("name", "A 的商品")
                .param("score", "10").param("rewardText", "")).path("code").asInt() != 200);
        // 我的商品里带着内容方便编辑
        JsonNode my = call(withToken(get("/api/market/my/items"), aToken)).path("detail");
        assertEquals("内容", my.get(0).path("reward").path("text").asText());
    }

    @Test
    public void adminRemove_blocksBuying_andSellerCannotRelist() throws Exception {
        User seller = userWithScore("mkrm", 0);
        User buyer = userWithScore("mkrmb", 100);
        String sToken = loginToken(seller.getUsername());
        int id = list(sToken, "违规商品", 10, -1, "内容");

        JsonNode adminList = call(withToken(get("/admin/market/item/list"), admin())).path("detail");
        assertTrue("后台能看到内容方便审核", adminList.toString().contains("违规商品") && adminList.toString().contains("内容"));
        assertEquals(200, call(withToken(post("/admin/market/item/remove"), admin()).param("id", String.valueOf(id))).path("code").asInt());

        assertTrue(buy(loginToken(buyer.getUsername()), id).path("code").asInt() != 200);
        assertTrue("管理员下架的卖家不能自己上架",
                call(withToken(post("/api/market/item/" + id + "/on"), sToken)).path("code").asInt() != 200);
        assertTrue("也不能改", call(withToken(post("/api/market/item"), sToken).param("id", String.valueOf(id))
                .param("name", "x").param("score", "1").param("rewardText", "y")).path("code").asInt() != 200);

        // 恢复后回到“卖家下架”，卖家可以自己再上架
        call(withToken(post("/admin/market/item/restore"), admin()).param("id", String.valueOf(id)));
        assertEquals(200, call(withToken(post("/api/market/item/" + id + "/on"), sToken)).path("code").asInt());
        assertEquals(200, buy(loginToken(buyer.getUsername()), id).path("code").asInt());
    }

    @Test
    public void concurrentBuyOfLastItem_onlyOneSucceeds_andScoreIsConserved() throws Exception {
        User seller = userWithScore("mkrace", 0);
        int id = list(loginToken(seller.getUsername()), "最后一份", 20, 1, "内容");
        int n = 6;
        List<User> users = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            User u = userWithScore("mkracer", 100);
            users.add(u);
            tokens.add(loginToken(u.getUsername()));
        }
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (String token : tokens) {
            fs.add(pool.submit(() -> {
                start.await();
                if (buy(token, id).path("code").asInt() == 200) ok.incrementAndGet();
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : fs) f.get();
        pool.shutdown();
        assertEquals(1, ok.get());
        int spent = 0;
        for (User u : users) spent += 100 - scoreOf(u);
        assertEquals("买家付出的 = 卖家收到的，积分守恒", 20, spent);
        assertEquals(20, scoreOf(seller));
    }

    @Test
    public void concurrentBuyBySameUser_scoreNeverNegative() throws Exception {
        User seller = userWithScore("mkrich", 0);
        int id = list(loginToken(seller.getUsername()), "不限量", 40, -1, "内容");
        User buyer = userWithScore("mkdup", 100); // 只够买 2 次
        String token = loginToken(buyer.getUsername());
        int n = 6;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            fs.add(pool.submit(() -> {
                start.await();
                if (buy(token, id).path("code").asInt() == 200) ok.incrementAndGet();
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : fs) f.get();
        pool.shutdown();
        assertEquals(2, ok.get());
        assertEquals(20, scoreOf(buyer));
        assertEquals(80, scoreOf(seller));
        assertEquals("失败的几次不能多算已卖数", 2,
                (int) jdbc().queryForObject("select sold from market_item where id = ?", Integer.class, id));
    }

    @Test
    public void requiresLogin() throws Exception {
        mvc.perform(get("/api/market/items")).andExpect(status().isOk());
        JsonNode r = json.readTree(mvc.perform(get("/api/market/items")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertTrue("没登录不能看", r.path("code").asInt() != 200);
    }
}
