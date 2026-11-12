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

    // 兑换。body: {"contact": "联系方式/收货信息（可不填）"}
    @PostMapping("/{id}")
    public Result exchange(@PathVariable Integer id, @RequestBody(required = false) Map<String, String> body) {
        User user = getApiUser();
        String contact = body == null ? null : body.get("contact");
        return success(exchangeService.exchange(user, id, contact));
    }

    // 我的兑换记录
    @GetMapping("/orders")
    public Result orders() {
        User user = getApiUser();
        return success(exchangeService.myOrders(user.getId()));
    }
}
