package co.yiiu.pybbs.service;

import co.yiiu.pybbs.model.User;

import java.util.Map;

public interface ICheckinService {

    /** 签到状态：是否开启、今天是否已签到、今天获得的积分、连续天数、积分范围 */
    Map<String, Object> status(User user);

    /** 签到：随机增加积分。已经签到过、功能没开启会抛 ApiException */
    Map<String, Object> checkin(User user);
}
