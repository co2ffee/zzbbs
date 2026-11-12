package co.yiiu.pybbs.controller.api;

import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.service.IExchangeService;
import co.yiiu.pybbs.util.Result;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 积分兑换：商品（名称、图片、所需积分）由后台配置，用户用积分兑换
 */
@RestController
@RequestMapping("/api/exchange")
public class ExchangeApiController extends BaseApiController {

    @Resource
    private IExchangeService exchangeService;

    // 商品列表（只有上架的）和当前用户的积分
    @GetMapping("/products")
    public Result products() {
        User user = getApiUser();
        Map<String, Object> map = new HashMap<>();
        map.put("products", exchangeService.listProducts());
        map.put("score", user.getScore());
        return success(map);
    }

    // 兑换：成功后返回后台提前设置好的内容 reward（文本/图片/视频，没设置就是 null）
    // 以前会带联系方式（body 里的 contact），现在不再收集；老前端还在发 body 也没关系，会被忽略
    @PostMapping("/{id}")
    public Result exchange(@PathVariable Integer id, @RequestBody(required = false) Map<String, String> ignored) {
        User user = getApiUser();
        return success(exchangeService.exchange(user, id));
    }

    // 我的兑换记录
    @GetMapping("/orders")
    public Result orders() {
        User user = getApiUser();
        return success(exchangeService.myOrders(user.getId()));
    }
}
