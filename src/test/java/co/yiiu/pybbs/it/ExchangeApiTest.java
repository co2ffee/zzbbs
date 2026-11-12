package co.yiiu.pybbs.it;

import co.yiiu.pybbs.model.AdminUser;
import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.service.IAdminUserService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import co.yiiu.pybbs.util.bcrypt.BCryptPasswordEncoder;

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

/** 积分兑换：后台配置商品，用户兑换（积分、库存、并发）、后台处理/取消（退积分） */
public class ExchangeApiTest extends AbstractApiTest {

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    private String adminToken;

    @Autowired
    private IAdminUserService adminUserService;

    // 在库里建一个超级管理员（密码用运行时随机生成的 PASSWORD），不在源码里放账号密码字面量
    private String admin() throws Exception {
        if (adminToken == null) {
            String username = unique("exadmin");
            AdminUser adminUser = new AdminUser();
            adminUser.setUsername(username);
            adminUser.setPassword(new BCryptPasswordEncoder().encode(PASSWORD));
            adminUser.setInTime(new Date());
            adminUser.setRoleId(1); // 超级管理员
            adminUserService.insert(adminUser);
            String body = mvc.perform(post("/admin/api/login").sessionAttr("_captcha", CAPTCHA)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("username", username).param("password", PASSWORD).param("code", CAPTCHA))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            adminToken = json.readTree(body).path("detail").path("token").asText();
        }
        return adminToken;
    }

    private JsonNode call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b) throws Exception {
        return json.readTree(mvc.perform(b).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
    }

    /** 后台新增商品，返回商品 id */
    private int newProduct(String name, int score, int stock, boolean enabled) throws Exception {
        JsonNode r = call(withToken(post("/admin/exchange/product/save"), admin())
                .param("name", name).param("score", String.valueOf(score)).param("stock", String.valueOf(stock))
                .param("enabled", String.valueOf(enabled)).param("description", "简介"));
        assertEquals(r.toString(), 200, r.path("code").asInt());
        return r.path("detail").path("id").asInt();
    }

    private User userWithScore(String prefix, int score) {
        User u = createUser(prefix);
        jdbc().update("update user set score = ? where id = ?", score, u.getId());
        return u;
    }

    private int scoreOf(User u) {
        return jdbc().queryForObject("select score from user where id = ?", Integer.class, u.getId());
    }

    private int stockOf(int productId) {
        return jdbc().queryForObject("select stock from exchange_product where id = ?", Integer.class, productId);
    }

    private JsonNode exchange(String token, int productId, String contact) throws Exception {
        return call(withToken(post("/api/exchange/" + productId), token).contentType(MediaType.APPLICATION_JSON)
                .content(contact == null ? "{}" : "{\"contact\":\"" + contact + "\"}"));
    }

    @Test
    public void exchange_deductsScoreAndStock_andCreatesOrderSnapshot() throws Exception {
        int pid = newProduct("马克杯", 30, 5, true);
        User user = userWithScore("exok", 100);
        String token = loginToken(user.getUsername());

        JsonNode r = exchange(token, pid, "微信 abc123");
        assertEquals(r.toString(), 200, r.path("code").asInt());
        assertEquals(30, r.path("detail").path("cost").asInt());
        assertEquals(70, r.path("detail").path("totalScore").asInt());
        assertEquals(70, scoreOf(user));
        assertEquals(4, stockOf(pid));

        // 商品改名、改价、删除都不影响历史记录（快照）
        call(withToken(post("/admin/exchange/product/save"), admin()).param("id", String.valueOf(pid))
                .param("name", "改了名字").param("score", "999").param("stock", "4"));
        JsonNode orders = call(withToken(get("/api/exchange/orders"), token)).path("detail");
        assertEquals(1, orders.size());
        assertEquals("马克杯", orders.get(0).path("productName").asText());
        assertEquals(30, orders.get(0).path("score").asInt());
        assertEquals("微信 abc123", orders.get(0).path("contact").asText());
        assertEquals(0, orders.get(0).path("status").asInt());
    }

    @Test
    public void exchange_rejectsWhenScoreNotEnough_withoutChangingAnything() throws Exception {
        int pid = newProduct("贵的", 500, 3, true);
        User user = userWithScore("expoor", 10);
        JsonNode r = exchange(loginToken(user.getUsername()), pid, null);
        assertEquals(201, r.path("code").asInt());
        assertEquals(10, scoreOf(user));
        assertEquals("积分不够时库存也不能少", 3, stockOf(pid));
        assertEquals(0, jdbc().queryForObject("select count(*) from exchange_order where user_id = ?", Integer.class, user.getId()).intValue());
    }

    @Test
    public void soldOut_disabled_andUnlimited() throws Exception {
        User user = userWithScore("exmisc", 1000);
        String token = loginToken(user.getUsername());

        int last = newProduct("最后一个", 10, 1, true);
        assertEquals(200, exchange(token, last, null).path("code").asInt());
        JsonNode soldOut = exchange(token, last, null);
        assertEquals(201, soldOut.path("code").asInt());
        assertEquals(990, scoreOf(user));

        int off = newProduct("已下架", 10, -1, false);
        assertEquals(201, exchange(token, off, null).path("code").asInt());

        int unlimited = newProduct("不限量", 10, -1, true);
        for (int i = 0; i < 3; i++) assertEquals(200, exchange(token, unlimited, null).path("code").asInt());
        assertEquals("不限量商品的库存保持 -1", -1, stockOf(unlimited));
        assertEquals(960, scoreOf(user));

        // 用户端列表：下架的看不到，已兑完的显示 soldOut，不限量的显示 unlimited
        JsonNode list = call(withToken(get("/api/exchange/products"), token)).path("detail");
        assertEquals(960, list.path("score").asInt());
        boolean sawOff = false, sawSoldOut = false, sawUnlimited = false;
        for (JsonNode p : list.path("products")) {
            String n = p.path("name").asText();
            if (n.equals("已下架")) sawOff = true;
            if (n.equals("最后一个")) sawSoldOut = p.path("soldOut").asBoolean();
            if (n.equals("不限量")) sawUnlimited = p.path("unlimited").asBoolean();
        }
        assertFalse("下架的商品不应该出现在用户端", sawOff);
        assertTrue(sawSoldOut);
        assertTrue(sawUnlimited);
    }

    /** 最后一件商品、多个人同时兑换：只有一个人成功，库存不会变成负数，没抢到的人积分不变 */
    @Test
    public void concurrentExchangeOfLastItem_onlyOneSucceeds() throws Exception {
        int pid = newProduct("限量一件", 20, 1, true);
        int n = 6;
        List<User> users = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            User u = userWithScore("exrace", 100);
            users.add(u);
            tokens.add(loginToken(u.getUsername()));
        }
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String token = tokens.get(i);
            fs.add(pool.submit(() -> {
                start.await();
                if (exchange(token, pid, null).path("code").asInt() == 200) ok.incrementAndGet();
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : fs) f.get();
        pool.shutdown();
        assertEquals("最后一件只能被一个人兑换", 1, ok.get());
        assertEquals(0, stockOf(pid));
        int spent = 0;
        for (User u : users) spent += 100 - scoreOf(u);
        assertEquals("只有成功的那个人被扣了积分", 20, spent);
    }

    /** 同一个人同时兑换多次：积分不够的那几次必须失败，积分不能变成负数 */
    @Test
    public void concurrentExchangeBySameUser_scoreNeverNegative() throws Exception {
        int pid = newProduct("不限量的小东西", 40, -1, true);
        User user = userWithScore("exsame", 100); // 只够兑换 2 次
        String token = loginToken(user.getUsername());
        int n = 6;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            fs.add(pool.submit(() -> {
                start.await();
                if (exchange(token, pid, null).path("code").asInt() == 200) ok.incrementAndGet();
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : fs) f.get();
        pool.shutdown();
        assertEquals(2, ok.get());
        assertEquals(20, scoreOf(user));
    }

    @Test
    public void adminDone_andCancelRefundsOnce() throws Exception {
        int pid = newProduct("可退的", 25, 2, true);
        User user = userWithScore("excancel", 100);
        String token = loginToken(user.getUsername());
        exchange(token, pid, "地址1");
        exchange(token, pid, "地址2");
        assertEquals(50, scoreOf(user));
        assertEquals(0, stockOf(pid));

        JsonNode orders = call(withToken(get("/admin/exchange/order/list").param("username", user.getUsername()), admin()))
                .path("detail").path("records");
        assertEquals(2, orders.size());
        int cancelId = orders.get(0).path("id").asInt(); // 按 id 倒序，第一个是“地址2”
        int doneId = orders.get(1).path("id").asInt();
        assertEquals(user.getUsername(), orders.get(0).path("username").asText());

        // 标记已处理；已处理的不能再取消，也不能重复标记
        JsonNode done = call(withToken(post("/admin/exchange/order/done"), admin()).param("id", String.valueOf(doneId)).param("remark", "已发货"));
        assertEquals(200, done.path("code").asInt());
        assertEquals(201, call(withToken(post("/admin/exchange/order/cancel"), admin()).param("id", String.valueOf(doneId))).path("code").asInt());
        assertEquals(201, call(withToken(post("/admin/exchange/order/done"), admin()).param("id", String.valueOf(doneId))).path("code").asInt());
        assertEquals(50, scoreOf(user));

        // 取消待处理的：退积分、库存加回；重复取消不会重复退
        assertEquals(200, call(withToken(post("/admin/exchange/order/cancel"), admin()).param("id", String.valueOf(cancelId)).param("remark", "缺货")).path("code").asInt());
        assertEquals(75, scoreOf(user));
        assertEquals(1, stockOf(pid));
        assertEquals(201, call(withToken(post("/admin/exchange/order/cancel"), admin()).param("id", String.valueOf(cancelId))).path("code").asInt());
        assertEquals("重复取消不能重复退积分", 75, scoreOf(user));
        assertEquals(1, stockOf(pid));

        // 用户端能看到状态和备注
        JsonNode mine = call(withToken(get("/api/exchange/orders"), token)).path("detail");
        boolean sawCanceled = false, sawDone = false;
        for (JsonNode o : mine) {
            if (o.path("status").asInt() == 2 && "缺货".equals(o.path("remark").asText())) sawCanceled = true;
            if (o.path("status").asInt() == 1 && "已发货".equals(o.path("remark").asText())) sawDone = true;
        }
        assertTrue(sawCanceled && sawDone);
    }

    @Test
    public void adminProductValidation_andImageMustBeOwnStorage() throws Exception {
        // 名称、积分必填且合法
        assertEquals(201, call(withToken(post("/admin/exchange/product/save"), admin()).param("score", "10")).path("code").asInt());
        assertEquals(201, call(withToken(post("/admin/exchange/product/save"), admin()).param("name", "x")).path("code").asInt());
        assertEquals(201, call(withToken(post("/admin/exchange/product/save"), admin()).param("name", "x").param("score", "0")).path("code").asInt());
        assertEquals(201, call(withToken(post("/admin/exchange/product/save"), admin()).param("name", "x").param("score", "5").param("stock", "-5")).path("code").asInt());
        // 图片只能是自己存储域名下的地址
        JsonNode evil = call(withToken(post("/admin/exchange/product/save"), admin()).param("name", "x").param("score", "5")
                .param("image", "http://evil.example.com/a.png"));
        assertEquals(evil.toString(), 201, evil.path("code").asInt());

        // 列表、删除
        int pid = newProduct("要删除的", 5, -1, true);
        assertEquals(200, call(withToken(get("/admin/exchange/product/delete").param("id", String.valueOf(pid)), admin())).path("code").asInt());
        assertEquals(201, call(withToken(get("/admin/exchange/product/get").param("id", String.valueOf(pid)), admin())).path("code").asInt());
    }

    @Test
    public void userCannotUseAdminApi_andExchangeRequiresLogin() throws Exception {
        String userToken = loginToken(createUser("exnoadmin").getUsername());
        mvc.perform(withToken(get("/admin/exchange/product/list"), userToken)).andExpect(status().isUnauthorized());
        JsonNode r = json.readTree(mvc.perform(post("/api/exchange/1")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertTrue(r.toString(), r.path("code").asInt() != 200);
    }
}
