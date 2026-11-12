package co.yiiu.pybbs.it;

import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.service.ISystemConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 每日签到：随机积分、范围可配置、每天只能签一次、开关、并发 */
public class CheckinApiTest extends AbstractApiTest {

    @Autowired
    private ISystemConfigService systemConfigService;

    private void setConfig(String key, String value) {
        Map<String, String> m = new HashMap<>();
        m.put("name", key);
        m.put("value", value);
        systemConfigService.update(Collections.singletonList(m));
    }

    private void setRange(int min, int max) {
        setConfig("checkin_score_min", String.valueOf(min));
        setConfig("checkin_score_max", String.valueOf(max));
    }

    @After
    public void restoreConfig() {
        setConfig("checkin_enabled", "1");
        setRange(1, 10);
    }

    private JsonNode checkin(String token) throws Exception {
        return json.readTree(mvc.perform(withToken(post("/api/checkin"), token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    public void defaultConfigExists_andStatusBeforeCheckin() throws Exception {
        String token = loginToken(createUser("ckstatus").getUsername());
        mvc.perform(withToken(get("/api/checkin/status"), token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.detail.enabled").value(true))
                .andExpect(jsonPath("$.detail.checkedIn").value(false))
                .andExpect(jsonPath("$.detail.streak").value(0))
                .andExpect(jsonPath("$.detail.scoreMin").value(1))
                .andExpect(jsonPath("$.detail.scoreMax").value(10));
    }

    @Test
    public void checkin_addsScoreWithinRange_andOnlyOncePerDay() throws Exception {
        setRange(5, 5);
        User user = createUser("ckonce");
        int before = userService.selectById(user.getId()).getScore();
        String token = loginToken(user.getUsername());

        JsonNode first = checkin(token);
        assertEquals(first.toString(), 200, first.path("code").asInt());
        assertEquals(5, first.path("detail").path("score").asInt());
        assertEquals(1, first.path("detail").path("streak").asInt());
        assertEquals(before + 5, userService.selectById(user.getId()).getScore().intValue());
        assertEquals(before + 5, first.path("detail").path("totalScore").asInt());

        // 同一天再签：失败，不再加积分
        JsonNode second = checkin(token);
        assertEquals(201, second.path("code").asInt());
        assertEquals("今天已经签到过了", second.path("description").asText());
        assertEquals(before + 5, userService.selectById(user.getId()).getScore().intValue());

        mvc.perform(withToken(get("/api/checkin/status"), token))
                .andExpect(jsonPath("$.detail.checkedIn").value(true))
                .andExpect(jsonPath("$.detail.todayScore").value(5))
                .andExpect(jsonPath("$.detail.streak").value(1));
    }

    @Test
    public void score_isRandomAndStaysInConfiguredRange() throws Exception {
        setRange(3, 7);
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < 60; i++) {
            String token = loginToken(createUser("ckrange").getUsername());
            int score = checkin(token).path("detail").path("score").asInt();
            assertTrue("积分应在 3~7 之间: " + score, score >= 3 && score <= 7);
            seen.add(score);
        }
        // 60 次里 5 个取值都没出现过的概率可以忽略；至少出现 3 个不同的值说明确实是随机的
        assertTrue("应该是随机的，只看到: " + seen, seen.size() >= 3);
    }

    @Test
    public void minGreaterThanMax_isSwappedInsteadOfFailing() throws Exception {
        setRange(9, 2);
        for (int i = 0; i < 15; i++) {
            String token = loginToken(createUser("ckswap").getUsername());
            int score = checkin(token).path("detail").path("score").asInt();
            assertTrue("积分应在 2~9 之间: " + score, score >= 2 && score <= 9);
        }
    }

    @Test
    public void invalidConfig_fallsBackToDefaultRange() throws Exception {
        setConfig("checkin_score_min", "abc");
        setConfig("checkin_score_max", "");
        for (int i = 0; i < 10; i++) {
            String token = loginToken(createUser("ckbad").getUsername());
            int score = checkin(token).path("detail").path("score").asInt();
            assertTrue("积分应在默认的 1~10 之间: " + score, score >= 1 && score <= 10);
        }
    }

    @Test
    public void disabled_rejectsCheckin() throws Exception {
        setConfig("checkin_enabled", "0");
        User user = createUser("ckoff");
        int before = userService.selectById(user.getId()).getScore();
        String token = loginToken(user.getUsername());
        JsonNode r = checkin(token);
        assertEquals(201, r.path("code").asInt());
        assertEquals(before, userService.selectById(user.getId()).getScore().intValue());
        mvc.perform(withToken(get("/api/checkin/status"), token)).andExpect(jsonPath("$.detail.enabled").value(false));
    }

    @Test
    public void requiresLogin() throws Exception {
        JsonNode r = json.readTree(mvc.perform(post("/api/checkin")).andReturn().getResponse().getContentAsString());
        assertTrue(r.toString(), r.path("code").asInt() != 200);
    }

    /** 并发重复提交：只有一次成功、只加一次积分（靠 (user_id, checkin_date) 唯一索引兜底） */
    @Test
    public void concurrentCheckins_onlyOneSucceeds() throws Exception {
        setRange(4, 4);
        User user = createUser("ckrace");
        int before = userService.selectById(user.getId()).getScore();
        String token = loginToken(user.getUsername());
        int n = 6;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            fs.add(pool.submit(() -> {
                start.await();
                if (checkin(token).path("code").asInt() == 200) ok.incrementAndGet();
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : fs) f.get();
        pool.shutdown();
        assertEquals("并发提交只应成功一次", 1, ok.get());
        assertEquals(before + 4, userService.selectById(user.getId()).getScore().intValue());
    }
}
