package co.yiiu.pybbs.controller.api;

import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.service.IMarketService;
import co.yiiu.pybbs.util.Result;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户集市：用户自己上架商品并定价，别的用户用积分购买，积分直接给卖家。
 * 商品内容（文本、图片、视频）付款后才给买家；文件用 /api/getUploadUrl 直传后把 fileUrl 带上来。
 */
@RestController
@RequestMapping("/api/market")
public class MarketApiController extends BaseApiController {

    @Resource
    private IMarketService marketService;

    // 上架中的商品，和当前用户的积分
    @GetMapping("/items")
    public Result items() {
        User user = getApiUser();
        Map<String, Object> map = new HashMap<>();
        map.put("items", marketService.listItems(user.getId()));
        map.put("score", user.getScore());
        return success(map);
    }

    // 我的商品（含内容，方便编辑）
    @GetMapping("/my/items")
    public Result myItems() {
        return success(marketService.myItems(getApiUser().getId()));
    }

    // 上架（不传 id）或修改（传 id）。修改时 image / rewardText 不传 = 不变，rewardText 传空字符串 = 清空，
    // removeImage / removeRewardFile = true 去掉图片 / 文件。至少要有文本或文件
    @PostMapping("/item")
    public Result save(Integer id, String name, String image, String description, Integer score, Integer stock,
                       Boolean removeImage, String rewardText, String rewardType, String rewardUrl,
                       Boolean removeRewardFile) {
        return success(marketService.save(getApiUser(), id, name, image, description, score, stock, removeImage,
                rewardText, rewardType, rewardUrl, removeRewardFile));
    }

    // 下架
    @PostMapping("/item/{id}/off")
    public Result off(@PathVariable Integer id) {
        marketService.setOnSale(getApiUser().getId(), id, false);
        return success();
    }

    // 重新上架
    @PostMapping("/item/{id}/on")
    public Result on(@PathVariable Integer id) {
        marketService.setOnSale(getApiUser().getId(), id, true);
        return success();
    }

    // 购买：成功后返回 reward（文本/图片/视频）和购买后的积分
    @PostMapping("/item/{id}/buy")
    public Result buy(@PathVariable Integer id) {
        return success(marketService.buy(getApiUser(), id));
    }

    // 我买到的（含内容）
    @GetMapping("/orders")
    public Result orders() {
        return success(marketService.myPurchases(getApiUser().getId()));
    }

    // 我卖出的（买家、收入）
    @GetMapping("/sales")
    public Result sales() {
        return success(marketService.mySales(getApiUser().getId()));
    }
}
