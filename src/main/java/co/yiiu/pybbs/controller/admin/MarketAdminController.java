package co.yiiu.pybbs.controller.admin;

import co.yiiu.pybbs.service.IMarketService;
import co.yiiu.pybbs.util.Result;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/** 后台：用户集市商品审核（可以看到内容），违规的下架 */
@Controller
@RequestMapping("/admin/market")
public class MarketAdminController extends BaseAdminController {

    @Resource
    private IMarketService marketService;

    // status：0 上架中，1 卖家下架，2 管理员下架；不传 = 全部
    @RequiresPermissions("market_item:list")
    @GetMapping("/item/list")
    @ResponseBody
    public Result list(@RequestParam(defaultValue = "1") Integer pageNo, Integer status) {
        return success(marketService.adminItems(pageNo, status));
    }

    // 管理员下架：卖家不能自己重新上架；已成交的订单不受影响（也不退款）
    @RequiresPermissions("market_item:edit")
    @PostMapping("/item/remove")
    @ResponseBody
    public Result remove(Integer id) {
        marketService.adminSetRemoved(id, true);
        return success();
    }

    // 恢复：回到“卖家下架”，由卖家决定要不要重新上架
    @RequiresPermissions("market_item:edit")
    @PostMapping("/item/restore")
    @ResponseBody
    public Result restore(Integer id) {
        marketService.adminSetRemoved(id, false);
        return success();
    }
}
