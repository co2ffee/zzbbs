package co.yiiu.pybbs.service;

import co.yiiu.pybbs.model.ExchangeProduct;
import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.util.MyPage;

import java.util.List;
import java.util.Map;

public interface IExchangeService {

    // ---------- 用户端 ----------
    /** 上架的商品，按 sort 从大到小 */
    List<Map<String, Object>> listProducts();

    /** 兑换：扣积分、减库存、生成兑换记录。积分不够、已兑完、商品下架会抛 ApiException */
    Map<String, Object> exchange(User user, Integer productId, String contact);

    /** 我的兑换记录 */
    List<Map<String, Object>> myOrders(Integer userId);

    // ---------- 后台 ----------
    MyPage<ExchangeProduct> selectProducts(Integer pageNo);

    ExchangeProduct selectProduct(Integer id);

    /** 新增/修改商品（id 为空是新增） */
    ExchangeProduct saveProduct(Integer id, String name, String image, String description, Integer score, Integer stock,
                                Integer sort, Boolean enabled, Boolean removeImage);

    void deleteProduct(Integer id);

    MyPage<Map<String, Object>> selectOrders(Integer pageNo, Integer status, String username);

    /** 标记已处理（比如已经发货/已经联系用户） */
    void markDone(Integer id, String remark);

    /** 取消：退还积分，限量商品库存加回去 */
    void cancel(Integer id, String remark);
}
