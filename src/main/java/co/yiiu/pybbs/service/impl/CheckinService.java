package co.yiiu.pybbs.service.impl;

import co.yiiu.pybbs.exception.ApiAssert;
import co.yiiu.pybbs.exception.ApiException;
import co.yiiu.pybbs.mapper.CheckinMapper;
import co.yiiu.pybbs.model.Checkin;
import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.service.ICheckinService;
import co.yiiu.pybbs.service.ISystemConfigService;
import co.yiiu.pybbs.service.IUserService;
import co.yiiu.pybbs.util.TodayRange;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
public class CheckinService implements ICheckinService {

    // 配置没填或填错时的默认范围
    private static final int DEFAULT_MIN = 1;
    private static final int DEFAULT_MAX = 10;

    @Resource
    private CheckinMapper checkinMapper;
    @Resource
    private ISystemConfigService systemConfigService;
    @Resource
    private IUserService userService;

    private boolean enabled() {
        return "1".equals(systemConfigService.selectAllConfig().get("checkin_enabled"));
    }

    private int parse(String key, int def) {
        try {
            return Integer.parseInt(systemConfigService.selectAllConfig().get(key).trim());
        } catch (Exception e) {
            return def;
        }
    }

    /** 随机范围 [min, max]：负数按 0 算；最小值比最大值大时自动对调，不让签到因为配置填反了就失败 */
    int[] range() {
        int min = Math.max(0, parse("checkin_score_min", DEFAULT_MIN));
        int max = Math.max(0, parse("checkin_score_max", DEFAULT_MAX));
        return min <= max ? new int[]{min, max} : new int[]{max, min};
    }

    private ZoneId zone() {
        return TodayRange.zone();
    }

    private Checkin find(Integer userId, String date) {
        QueryWrapper<Checkin> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(Checkin::getUserId, userId).eq(Checkin::getCheckinDate, date);
        return checkinMapper.selectOne(wrapper);
    }

    @Override
    public Map<String, Object> status(User user) {
        int[] range = range();
        Map<String, Object> map = new HashMap<>();
        map.put("enabled", enabled());
        map.put("scoreMin", range[0]);
        map.put("scoreMax", range[1]);
        LocalDate today = LocalDate.now(zone());
        Checkin todayRecord = find(user.getId(), today.toString());
        map.put("checkedIn", todayRecord != null);
        map.put("todayScore", todayRecord == null ? null : todayRecord.getScore());
        if (todayRecord != null) {
            map.put("streak", todayRecord.getStreak());
        } else {
            // 还没签到：展示到昨天为止的连续天数
            Checkin yesterday = find(user.getId(), today.minusDays(1).toString());
            map.put("streak", yesterday == null ? 0 : yesterday.getStreak());
        }
        return map;
    }

    @Override
    public Map<String, Object> checkin(User user) {
        ApiAssert.isTrue(enabled(), "签到功能没有开启");
        LocalDate today = LocalDate.now(zone());
        Checkin yesterday = find(user.getId(), today.minusDays(1).toString());

        int[] range = range();
        int score = ThreadLocalRandom.current().nextInt(range[0], range[1] + 1);

        Checkin checkin = new Checkin();
        checkin.setUserId(user.getId());
        checkin.setCheckinDate(today.toString());
        checkin.setScore(score);
        checkin.setStreak(yesterday == null ? 1 : yesterday.getStreak() + 1);
        checkin.setInTime(new Date());
        try {
            // 先插入记录：(user_id, checkin_date) 有唯一索引，并发重复提交只有一个能成功，后面的不会再加积分
            checkinMapper.insert(checkin);
        } catch (DuplicateKeyException e) {
            throw new ApiException("今天已经签到过了");
        }

        User latest = userService.selectByIdWithoutCache(user.getId());
        latest.setScore((latest.getScore() == null ? 0 : latest.getScore()) + score);
        userService.update(latest);

        Map<String, Object> map = new HashMap<>();
        map.put("score", score);
        map.put("streak", checkin.getStreak());
        map.put("totalScore", latest.getScore());
        return map;
    }
}
