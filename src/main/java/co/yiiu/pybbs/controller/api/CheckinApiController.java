package co.yiiu.pybbs.controller.api;

import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.service.ICheckinService;
import co.yiiu.pybbs.util.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 每日签到：随机获得积分，范围在后台“积分配置”里设置（checkin_score_min / checkin_score_max）
 */
@RestController
@RequestMapping("/api/checkin")
public class CheckinApiController extends BaseApiController {

    @Resource
    private ICheckinService checkinService;

    // 签到状态：是否开启、今天是否已签到、连续天数、积分范围
    @GetMapping("/status")
    public Result status() {
        User user = getApiUser();
        return success(checkinService.status(user));
    }

    // 签到
    @PostMapping
    public Result checkin() {
        User user = getApiUser();
        return success(checkinService.checkin(user));
    }
}
