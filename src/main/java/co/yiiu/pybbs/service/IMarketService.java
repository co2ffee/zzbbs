package co.yiiu.pybbs.service;

import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.util.MyPage;

import java.util.List;
import java.util.Map;

/** 用户集市：用户自己上架商品、自己定价，买家付积分，积分直接转给卖家 */
public interface IMarketService {

    /** 上架中的商品（不含付款后才给的内容，只告诉有哪些类型） */
    List<Map<String, Object>> listItems(Integer currentUserId);

    /** 我上架/下架过的商品（带内容，方便编辑，还有已卖数） */
    List<Map<String, Object>> myItems(Integer sellerId);

    /** 新增（id 为空）或修改；只能改自己的、没被管理员下架的商品 */
    Map<String, Object> save(User seller, Integer id, String name, String image, String description, Integer score,
                             Integer stock, Boolean removeImage, String rewardText, String rewardType,
                             String rewardUrl, Boolean removeRewardFile);

    /** 卖家上架 / 下架自己的商品（管理员下架的不能自己重新上架） */
    void setOnSale(Integer sellerId, Integer id, boolean onSale);

    /** 购买：买家积分转给卖家，返回 reward（付款后才给的内容）。积分不够、卖光、买自己的东西会抛 ApiException */
    Map<String, Object> buy(User buyer, Integer itemId);

    /** 我买到的 */
    List<Map<String, Object>> myPurchases(Integer buyerId);

    /** 我卖出的（谁买的、收入多少，不含内容） */
    List<Map<String, Object>> mySales(Integer sellerId);

    // ---------- 后台 ----------
    MyPage<Map<String, Object>> adminItems(Integer pageNo, Integer status);

    /** 管理员下架 / 恢复（恢复后回到“卖家下架”，由卖家自己决定要不要重新上架） */
    void adminSetRemoved(Integer id, boolean removed);
}
