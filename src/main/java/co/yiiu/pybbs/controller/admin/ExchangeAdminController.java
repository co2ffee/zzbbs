package co.yiiu.pybbs.controller.admin;

import co.yiiu.pybbs.exception.ApiAssert;
import co.yiiu.pybbs.model.ExchangeProduct;
import co.yiiu.pybbs.service.IExchangeService;
import co.yiiu.pybbs.util.FileUtil;
import co.yiiu.pybbs.util.Result;
import com.alibaba.fastjson.JSON;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 后台：积分兑换的商品（名称、图片、所需积分、库存）和兑换记录
 */
@Controller
@RequestMapping("/admin/exchange")
public class ExchangeAdminController extends BaseAdminController {

    @Resource
    private IExchangeService exchangeService;
    @Resource
    private FileUtil fileUtil;

    // ---------------- 商品 ----------------

    @RequiresPermissions("exchange_product:list")
    @GetMapping("/product/list")
    @ResponseBody
    public Result productList(@RequestParam(defaultValue = "1") Integer pageNo) {
        return success(exchangeService.selectProducts(pageNo));
    }

    @RequiresPermissions("exchange_product:list")
    @GetMapping("/product/get")
    @ResponseBody
    public Result productGet(Integer id) {
        ExchangeProduct product = exchangeService.selectProduct(id);
        ApiAssert.notNull(product, "商品不存在");
        return success(product);
    }

    // 新增（不传 id）或修改（传 id）。image 是直传后拿到的 fileUrl；修改时不传 image 保持原图
    @RequiresPermissions("exchange_product:edit")
    @PostMapping("/product/save")
    @ResponseBody
    public Result productSave(Integer id, String name, String image, String description, Integer score, Integer stock,
                              Integer sort, Boolean enabled) {
        return success(exchangeService.saveProduct(id, name, image, description, score, stock, sort, enabled));
    }

    // 商品图片的上传地址（预签名，和标签图标一样：浏览器直接 PUT 到存储桶，提交时只带 fileUrl）
    @RequiresPermissions("exchange_product:edit")
    @GetMapping("/product/upload_url")
    @ResponseBody
    public Result uploadUrl() {
        String json;
        try {
            json = fileUtil.awsBlazeUpload(null, "avatar", null);
        } catch (Exception e) {
            return error("获取上传地址失败，请检查 Backblaze 配置");
        }
        return success(JSON.parseObject(json));
    }

    @RequiresPermissions("exchange_product:delete")
    @GetMapping("/product/delete")
    @ResponseBody
    public Result productDelete(Integer id) {
        exchangeService.deleteProduct(id);
        return success();
    }

    // ---------------- 兑换记录 ----------------

    @RequiresPermissions("exchange_order:list")
    @GetMapping("/order/list")
    @ResponseBody
    public Result orderList(@RequestParam(defaultValue = "1") Integer pageNo, Integer status, String username) {
        if (username != null) username = username.replace("\"", "").replace("'", "");
        return success(exchangeService.selectOrders(pageNo, status, username));
    }

    // 标记已处理（比如已经发货、已经联系用户）
    @RequiresPermissions("exchange_order:edit")
    @PostMapping("/order/done")
    @ResponseBody
    public Result orderDone(Integer id, String remark) {
        exchangeService.markDone(id, remark);
        return success();
    }

    // 取消：退还积分，限量商品的库存加回去
    @RequiresPermissions("exchange_order:edit")
    @PostMapping("/order/cancel")
    @ResponseBody
    public Result orderCancel(Integer id, String remark) {
        exchangeService.cancel(id, remark);
        return success();
    }
}
