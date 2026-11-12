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

    private JsonNode exchange(String token, int productId) throws Exception {
        return call(withToken(post("/api/exchange/" + productId), token));
    }

    /** 后台新增带“兑换后给用户看的内容”的商品 */
    private int newRewardProduct(String name, int score, int stock, String text, String type, String url) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b = withToken(post("/admin/exchange/product/save"), admin())
                .param("name", name).param("score", String.valueOf(score)).param("stock", String.valueOf(stock));
        if (text != null) b.param("rewardText", text);
        if (type != null) b.param("rewardType", type);
        if (url != null) b.param("rewardUrl", url);
        JsonNode r = call(b);
        assertEquals(r.toString(), 200, r.path("code").asInt());
        return r.path("detail").path("id").asInt();
    }

    @Test
    public void exchange_deductsScoreAndStock_andCreatesOrderSnapshot() throws Exception {
        int pid = newProduct("马克杯", 30, 5, true);
        User user = userWithScore("exok", 100);
        String token = loginToken(user.getUsername());

        JsonNode r = exchange(token, pid);
        assertEquals(r.toString(), 200, r.path("code").asInt());
        assertEquals(30, r.path("detail").path("cost").asInt());
        assertEquals(70, r.path("detail").path("totalScore").asInt());
        assertTrue("没设置兑换内容就没有 reward", r.path("detail").path("reward").isNull());
        assertEquals(70, scoreOf(user));
        assertEquals(4, stockOf(pid));

        // 商品改名、改价都不影响历史记录（快照）；新记录直接是“已兑换”（1），不需要管理员处理，也不再有联系方式
        call(withToken(post("/admin/exchange/product/save"), admin()).param("id", String.valueOf(pid))
                .param("name", "改了名字").param("score", "999").param("stock", "4"));
        JsonNode orders = call(withToken(get("/api/exchange/orders"), token)).path("detail");
        assertEquals(1, orders.size());
        assertEquals("马克杯", orders.get(0).path("productName").asText());
        assertEquals(30, orders.get(0).path("score").asInt());
        assertEquals(1, orders.get(0).path("status").asInt());
        assertTrue("不再收集联系方式", orders.get(0).path("contact").isMissingNode());
    }

    /** 兑换后给用户看的内容：商品列表不泄露，兑换后立刻给，我的兑换里能再看到；没兑换的人、取消之后都看不到 */
    @Test
    public void reward_isDeliveredOnlyAfterExchange_andSnapshotted() throws Exception {
        String img = "http://localhost:8080/static/upload/secret.jpeg";
        int pid = newRewardProduct("教程", 10, -1, "兑换码：ABC-123", "image", img);
        User buyer = userWithScore("exrwd", 100);
        User other = userWithScore("exrwdother", 100);
        String buyerToken = loginToken(buyer.getUsername());
        String otherToken = loginToken(other.getUsername());

        // 商品列表只告诉有什么类型的内容，不给内容本身
        JsonNode listed = call(withToken(get("/api/exchange/products"), otherToken)).path("detail").path("products");
        JsonNode item = null;
        for (JsonNode p : listed) if (p.path("id").asInt() == pid) item = p;
        assertTrue(item != null);
        assertEquals("[\"text\",\"image\"]", item.path("delivers").toString());
        assertFalse(item.toString().contains("ABC-123"));
        assertFalse(item.toString().contains("secret.jpeg"));
        assertTrue(item.path("rewardText").isMissingNode() && item.path("rewardUrl").isMissingNode());

        // 兑换成功立刻拿到内容
        JsonNode r = exchange(buyerToken, pid);
        assertEquals(r.toString(), 200, r.path("code").asInt());
        assertEquals("兑换码：ABC-123", r.path("detail").path("reward").path("text").asText());
        assertEquals("image", r.path("detail").path("reward").path("type").asText());
        assertEquals(img, r.path("detail").path("reward").path("url").asText());

        // 我的兑换里能再看到；没兑换的人看不到别人的
        JsonNode mine = call(withToken(get("/api/exchange/orders"), buyerToken)).path("detail");
        assertEquals("兑换码：ABC-123", mine.get(0).path("reward").path("text").asText());
        assertEquals(img, mine.get(0).path("reward").path("url").asText());
        assertEquals(0, call(withToken(get("/api/exchange/orders"), otherToken)).path("detail").size());

        // 商品之后改了文本、去掉文件、甚至删除：已经兑换的用户看到的还是兑换时的内容（快照）
        call(withToken(post("/admin/exchange/product/save"), admin()).param("id", String.valueOf(pid)).param("name", "教程")
                .param("score", "10").param("rewardText", "改了").param("removeRewardFile", "true"));
        call(withToken(get("/admin/exchange/product/delete").param("id", String.valueOf(pid)), admin()));
        JsonNode after = call(withToken(get("/api/exchange/orders"), buyerToken)).path("detail");
        assertEquals("兑换码：ABC-123", after.get(0).path("reward").path("text").asText());
        assertEquals(img, after.get(0).path("reward").path("url").asText());

        // 管理员取消：积分退还，用户也看不到内容了
        int orderId = after.get(0).path("id").asInt();
        assertEquals(200, call(withToken(post("/admin/exchange/order/cancel"), admin()).param("id", String.valueOf(orderId))).path("code").asInt());
        assertEquals(100, scoreOf(buyer));
        JsonNode canceled = call(withToken(get("/api/exchange/orders"), buyerToken)).path("detail").get(0);
        assertEquals(2, canceled.path("status").asInt());
        assertTrue("取消之后不再给内容: " + canceled, canceled.path("reward").isNull());
    }

    @Test
    public void rewardEditing_textClearKeepRemoveAndValidation() throws Exception {
        String video = "http://localhost:8080/static/upload/v.mp4";
        int pid = newRewardProduct("视频课", 5, -1, "说明", "video", video);
        java.util.function.Supplier<JsonNode> get = () -> {
            try { return call(withToken(get("/admin/exchange/product/get").param("id", String.valueOf(pid)), admin())).path("detail"); }
            catch (Exception e) { throw new AssertionError(e); }
        };
        assertEquals("说明", get.get().path("rewardText").asText());
        assertEquals("video", get.get().path("rewardType").asText());

        // 修改时不传 reward 相关参数：全都保持不变
        call(withToken(post("/admin/exchange/product/save"), admin()).param("id", String.valueOf(pid)).param("name", "视频课").param("score", "5"));
        assertEquals("说明", get.get().path("rewardText").asText());
        assertEquals(video, get.get().path("rewardUrl").asText());

        // 传空字符串：清空文本，文件还在
        call(withToken(post("/admin/exchange/product/save"), admin()).param("id", String.valueOf(pid)).param("name", "视频课").param("score", "5").param("rewardText", ""));
        assertTrue(get.get().path("rewardText").isNull());
        assertEquals(video, get.get().path("rewardUrl").asText());

        // removeRewardFile：去掉文件
        call(withToken(post("/admin/exchange/product/save"), admin()).param("id", String.valueOf(pid)).param("name", "视频课").param("score", "5").param("removeRewardFile", "true"));
        assertTrue(get.get().path("rewardUrl").isNull() && get.get().path("rewardType").isNull());

        // 校验：文件必须是自己存储域名、类型只能是图片或视频、文本不能太长
        assertEquals(201, call(withToken(post("/admin/exchange/product/save"), admin()).param("name", "x").param("score", "5")
                .param("rewardUrl", "http://evil.example.com/a.mp4").param("rewardType", "video")).path("code").asInt());
        assertEquals(201, call(withToken(post("/admin/exchange/product/save"), admin()).param("name", "x").param("score", "5")
                .param("rewardUrl", video).param("rewardType", "exe")).path("code").asInt());
        assertEquals(201, call(withToken(post("/admin/exchange/product/save"), admin()).param("name", "x").param("score", "5")
                .param("rewardText", new String(new char[5001]).replace('\0', 'a'))).path("code").asInt());
    }

    @Test
    public void exchange_rejectsWhenScoreNotEnough_withoutChangingAnything() throws Exception {
        int pid = newProduct("贵的", 500, 3, true);
        User user = userWithScore("expoor", 10);
        JsonNode r = exchange(loginToken(user.getUsername()), pid);
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
        assertEquals(200, exchange(token, last).path("code").asInt());
        JsonNode soldOut = exchange(token, last);
        assertEquals(201, soldOut.path("code").asInt());
        assertEquals(990, scoreOf(user));

        int off = newProduct("已下架", 10, -1, false);
        assertEquals(201, exchange(token, off).path("code").asInt());

        int unlimited = newProduct("不限量", 10, -1, true);
        for (int i = 0; i < 3; i++) assertEquals(200, exchange(token, unlimited).path("code").asInt());
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
                if (exchange(token, pid).path("code").asInt() == 200) ok.incrementAndGet();
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
                if (exchange(token, pid).path("code").asInt() == 200) ok.incrementAndGet();
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
    public void adminCancel_refundsOnce_andListStatusesAreNumbers() throws Exception {
        int pid = newProduct("可退的", 25, 2, true);
        User user = userWithScore("excancel", 100);
        String token = loginToken(user.getUsername());
        exchange(token, pid);
        exchange(token, pid);
        assertEquals(50, scoreOf(user));
        assertEquals(0, stockOf(pid));

        JsonNode orders = call(withToken(get("/admin/exchange/order/list").param("username", user.getUsername()), admin()))
                .path("detail").path("records");
        assertEquals(2, orders.size());
        int cancelId = orders.get(0).path("id").asInt();
        assertEquals(user.getUsername(), orders.get(0).path("username").asText());
        // 状态必须是数字（MySQL 驱动会把 tinyint(1) 当布尔返回，管理端就认不出来了）；新记录直接是“已兑换” 1
        assertTrue("status 应该是数字: " + orders.get(0), orders.get(0).path("status").isNumber());
        assertEquals(1, orders.get(0).path("status").asInt());
        assertTrue("后台列表不再有联系方式", orders.get(0).path("contact").isMissingNode());

        // “标记已处理”已经没有了（兑换后自动发放）
        mvc.perform(withToken(post("/admin/exchange/order/done"), admin()).param("id", String.valueOf(cancelId)))
                .andExpect(status().is4xxClientError());

        // 取消：退积分、库存加回、备注用户能看到；重复取消不会重复退
        assertEquals(200, call(withToken(post("/admin/exchange/order/cancel"), admin()).param("id", String.valueOf(cancelId)).param("remark", "缺货")).path("code").asInt());
        assertEquals(75, scoreOf(user));
        assertEquals(1, stockOf(pid));
        assertEquals(201, call(withToken(post("/admin/exchange/order/cancel"), admin()).param("id", String.valueOf(cancelId))).path("code").asInt());
        assertEquals("重复取消不能重复退积分", 75, scoreOf(user));
        assertEquals(1, stockOf(pid));

        // 后台列表里：已兑换是 1、已取消是 2，能区分开
        JsonNode after = call(withToken(get("/admin/exchange/order/list").param("username", user.getUsername()), admin())).path("detail").path("records");
        java.util.Set<Integer> statuses = new java.util.HashSet<>();
        for (JsonNode o : after) { assertTrue(o.path("status").isNumber()); statuses.add(o.path("status").asInt()); }
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(1, 2)), statuses);

        JsonNode mine = call(withToken(get("/api/exchange/orders"), token)).path("detail");
        boolean sawCanceled = false;
        for (JsonNode o : mine) if (o.path("status").asInt() == 2 && "缺货".equals(o.path("remark").asText())) sawCanceled = true;
        assertTrue(sawCanceled);
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

    /** 去掉商品图片：不传 image 保持原图；removeImage=true 去掉；用户端也看不到图片了；同时传新图片以新图片为准 */
    @Test
    public void removeProductImage() throws Exception {
        // 种子数据里的 static_url 就是“自己存储域名”，用它下面的地址当图片
        String img1 = "http://localhost:8080/static/upload/a.png";
        String img2 = "http://localhost:8080/static/upload/b.png";
        JsonNode created = call(withToken(post("/admin/exchange/product/save"), admin()).param("name", "有图的").param("score", "5")
                .param("image", img1));
        assertEquals(created.toString(), 200, created.path("code").asInt());
        int pid = created.path("detail").path("id").asInt();
        String userToken = loginToken(createUser("eximg").getUsername());
        java.util.function.Supplier<String> userSeesImage = () -> {
            try {
                for (JsonNode p : call(withToken(get("/api/exchange/products"), userToken)).path("detail").path("products")) {
                    if (p.path("id").asInt() == pid) return p.path("image").isNull() ? null : p.path("image").asText();
                }
            } catch (Exception e) { throw new AssertionError(e); }
            throw new AssertionError("用户端应该能看到这个商品");
        };
        assertEquals(img1, userSeesImage.get());

        // 修改时不传 image：保持原图
        call(withToken(post("/admin/exchange/product/save"), admin()).param("id", String.valueOf(pid)).param("name", "有图的").param("score", "5"));
        assertEquals(img1, userSeesImage.get());

        // removeImage=true：去掉图片
        JsonNode removed = call(withToken(post("/admin/exchange/product/save"), admin()).param("id", String.valueOf(pid))
                .param("name", "有图的").param("score", "5").param("removeImage", "true"));
        assertEquals(removed.toString(), 200, removed.path("code").asInt());
        assertEquals(null, userSeesImage.get());
        assertTrue(call(withToken(get("/admin/exchange/product/get").param("id", String.valueOf(pid)), admin())).path("detail").path("image").isNull());

        // 去掉之后再不传 image 保存，仍然没有图片；再上传新图片就有了
        call(withToken(post("/admin/exchange/product/save"), admin()).param("id", String.valueOf(pid)).param("name", "有图的").param("score", "5"));
        assertEquals(null, userSeesImage.get());
        call(withToken(post("/admin/exchange/product/save"), admin()).param("id", String.valueOf(pid)).param("name", "有图的").param("score", "5").param("image", img2));
        assertEquals(img2, userSeesImage.get());

        // 同时传了新图片和 removeImage：以新图片为准
        call(withToken(post("/admin/exchange/product/save"), admin()).param("id", String.valueOf(pid)).param("name", "有图的").param("score", "5")
                .param("image", img1).param("removeImage", "true"));
        assertEquals(img1, userSeesImage.get());
    }
}
