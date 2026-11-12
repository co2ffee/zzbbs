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

    /** 兑换：扣积分、减库存、生成兑换记录，并把后台提前设置的内容（文本/图片/视频）发给用户。积分不够、已兑完、商品下架会抛 ApiException */
    Map<String, Object> exchange(User user, Integer productId);

    /** 我的兑换记录（没取消的带上兑换到的内容） */
    List<Map<String, Object>> myOrders(Integer userId);

    // ---------- 后台 ----------
    MyPage<ExchangeProduct> selectProducts(Integer pageNo);

    ExchangeProduct selectProduct(Integer id);

    /**
     * 新增/修改商品（id 为空是新增）。
     * 兑换后给用户看的内容：rewardText 文本（修改时不传 = 不变，传空字符串 = 清空）；
     * rewardUrl + rewardType（image / video）是直传后拿到的文件；removeRewardFile=true 去掉文件。
     */
    ExchangeProduct saveProduct(Integer id, String name, String image, String description, Integer score, Integer stock,
                                Integer sort, Boolean enabled, Boolean removeImage, String rewardText, String rewardType,
                                String rewardUrl, Boolean removeRewardFile);

    void deleteProduct(Integer id);

    MyPage<Map<String, Object>> selectOrders(Integer pageNo, Integer status, String username);

    /** 取消：退还积分，限量商品库存加回去，用户也看不到兑换到的内容了 */
    void cancel(Integer id, String remark);
}
