package co.yiiu.pybbs.service.impl;

import co.yiiu.pybbs.exception.ApiAssert;
import co.yiiu.pybbs.mapper.MarketItemMapper;
import co.yiiu.pybbs.mapper.MarketOrderMapper;
import co.yiiu.pybbs.mapper.UserMapper;
import co.yiiu.pybbs.model.MarketItem;
import co.yiiu.pybbs.model.MarketOrder;
import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.service.IMarketService;
import co.yiiu.pybbs.service.ISystemConfigService;
import co.yiiu.pybbs.service.IUserService;
import co.yiiu.pybbs.util.FileUtil;
import co.yiiu.pybbs.util.MyPage;
import co.yiiu.pybbs.util.SpringContextUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class MarketService implements IMarketService {

    private static final int MAX_SCORE = 10_000_000;
    private static final int MAX_STOCK = 1_000_000;
    private static final int MAX_TEXT = 5000;
    /** 每个用户同时最多挂这么多件（没被管理员下架的），防止刷屏 */
    private static final int MAX_ITEMS_PER_USER = 20;

    @Resource
    private MarketItemMapper itemMapper;
    @Resource
    private MarketOrderMapper orderMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private IUserService userService;
    @Resource
    private ISystemConfigService systemConfigService;
    @Resource
    private FileUtil fileUtil;

    // ---------------------------------------------------------------- 工具

    /** 付款后才给的内容：文本、文件类型（image / video）、文件地址；都没有就是 null */
    private Map<String, Object> reward(String text, String type, String url) {
        if (StringUtils.isEmpty(text) && StringUtils.isEmpty(url)) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", StringUtils.isEmpty(text) ? null : text);
        m.put("type", StringUtils.isEmpty(url) ? null : type);
        m.put("url", StringUtils.isEmpty(url) ? null : url);
        return m;
    }

    private List<String> delivers(MarketItem i) {
        List<String> list = new ArrayList<>();
        if (!StringUtils.isEmpty(i.getRewardText())) list.add("text");
        if (!StringUtils.isEmpty(i.getRewardUrl())) list.add(i.getRewardType());
        return list;
    }

    /** 一次查出一批用户，避免每行查一次 */
    private Map<Integer, User> usersById(Collection<Integer> ids) {
        if (ids.isEmpty()) return new HashMap<>();
        return userMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private void refreshUserCache(Integer userId) {
        User user = userService.selectByIdWithoutCache(userId);
        // 积分是 SQL 直接改的，通知一下缓存插件（没装插件时是空方法）
        if (user != null) SpringContextUtil.getBean(UserService.class).delRedisUser(user);
    }

    private Map<String, Object> itemMap(MarketItem i, Map<Integer, User> sellers, Integer currentUserId, boolean withReward) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId());
        m.put("name", i.getName());
        m.put("image", i.getImage());
        m.put("description", i.getDescription());
        m.put("score", i.getScore());
        m.put("unlimited", i.getStock() < 0);
        m.put("stock", i.getStock());
        m.put("soldOut", i.getStock() == 0);
        m.put("sold", i.getSold());
        m.put("status", i.getStatus());
        m.put("delivers", delivers(i));
        m.put("sellerId", i.getSellerId());
        User seller = sellers.get(i.getSellerId());
        m.put("sellerName", seller == null ? null : seller.getUsername());
        m.put("sellerAvatar", seller == null ? null : seller.getAvatar());
        m.put("mine", i.getSellerId().equals(currentUserId));
        m.put("inTime", i.getInTime());
        // 内容只给卖家自己编辑用（或后台审核），列表里的买家看不到
        if (withReward) m.put("reward", reward(i.getRewardText(), i.getRewardType(), i.getRewardUrl()));
        return m;
    }

    // ---------------------------------------------------------------- 用户端：浏览与上架

    @Override
    public List<Map<String, Object>> listItems(Integer currentUserId) {
        QueryWrapper<MarketItem> wrapper = new QueryWrapper<>();
        wrapper.eq("status", MarketItem.ON_SALE).orderByDesc("id").last("limit 200");
        List<MarketItem> items = itemMapper.selectList(wrapper);
        Map<Integer, User> sellers = usersById(items.stream().map(MarketItem::getSellerId).collect(Collectors.toSet()));
        return items.stream().map(i -> itemMap(i, sellers, currentUserId, false)).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> myItems(Integer sellerId) {
        QueryWrapper<MarketItem> wrapper = new QueryWrapper<>();
        wrapper.eq("seller_id", sellerId).orderByDesc("id");
        Map<Integer, User> sellers = usersById(java.util.Collections.singleton(sellerId));
        return itemMapper.selectList(wrapper).stream().map(i -> itemMap(i, sellers, sellerId, true)).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> save(User seller, Integer id, String name, String image, String description, Integer score,
                                    Integer stock, Boolean removeImage, String rewardText, String rewardType,
                                    String rewardUrl, Boolean removeRewardFile) {
        ApiAssert.isTrue(Boolean.TRUE.equals(seller.getActive()), "你的帐号还没有激活，激活后才能上架商品");
        name = name == null ? "" : name.trim();
        ApiAssert.isTrue(name.length() >= 1 && name.length() <= 100, "商品名称要填，最多 100 个字");
        ApiAssert.notNull(score, "请填写价格");
        ApiAssert.isTrue(score >= 1 && score <= MAX_SCORE, "价格要在 1 ~ " + MAX_SCORE + " 积分之间");
        if (stock == null) stock = -1;
        ApiAssert.isTrue(stock == -1 || (stock >= 0 && stock <= MAX_STOCK), "库存要在 0 ~ " + MAX_STOCK + " 之间，-1 表示不限量");
        if (description != null) {
            description = description.trim();
            ApiAssert.isTrue(description.length() <= 500, "商品简介最多 500 个字");
        }
        if (!StringUtils.isEmpty(image)) {
            ApiAssert.isTrue(image.length() <= 500, "图片地址太长");
            ApiAssert.isTrue(fileUtil.isOwnStorageUrl(image), "图片地址不合法");
        }
        boolean clearText = false;
        if (rewardText != null) {
            rewardText = rewardText.trim();
            ApiAssert.isTrue(rewardText.length() <= MAX_TEXT, "付款后给买家看的文本最多 " + MAX_TEXT + " 个字");
            clearText = rewardText.isEmpty();
        }
        if (!StringUtils.isEmpty(rewardUrl)) {
            ApiAssert.isTrue("image".equals(rewardType) || "video".equals(rewardType), "文件类型只能是图片或视频");
            ApiAssert.isTrue(rewardUrl.length() <= 500, "文件地址太长");
            ApiAssert.isTrue(fileUtil.isOwnStorageUrl(rewardUrl), "文件地址不合法");
        }

        MarketItem item;
        if (id == null) {
            QueryWrapper<MarketItem> count = new QueryWrapper<>();
            count.eq("seller_id", seller.getId()).ne("status", MarketItem.OFF_BY_ADMIN);
            ApiAssert.isTrue(itemMapper.selectCount(count) < MAX_ITEMS_PER_USER,
                    "你最多同时挂 " + MAX_ITEMS_PER_USER + " 件商品，先下架或删掉一些再上架");
            item = new MarketItem();
            item.setSellerId(seller.getId());
            item.setSold(0);
            item.setStatus(MarketItem.ON_SALE);
            item.setInTime(new Date());
        } else {
            item = itemMapper.selectById(id);
            ApiAssert.isTrue(item != null && item.getSellerId().equals(seller.getId()), "商品不存在");
            ApiAssert.isTrue(item.getStatus() != MarketItem.OFF_BY_ADMIN, "这个商品被管理员下架了，不能修改");
        }

        // 最终要有内容可卖（文本或文件至少一样），否则买家付了积分什么也拿不到
        boolean hasText = !clearText && (rewardText != null ? !rewardText.isEmpty() : !StringUtils.isEmpty(item.getRewardText()));
        boolean hasFile = !StringUtils.isEmpty(rewardUrl)
                || (!StringUtils.isEmpty(item.getRewardUrl()) && !Boolean.TRUE.equals(removeRewardFile));
        ApiAssert.isTrue(hasText || hasFile, "请填写付款后给买家看的内容（文本，或者图片/视频）");

        item.setName(name);
        if (!StringUtils.isEmpty(image)) item.setImage(image);
        item.setDescription(StringUtils.isEmpty(description) ? null : description);
        item.setScore(score);
        item.setStock(stock);
        item.setUpdateTime(new Date());
        if (rewardText != null && !clearText) item.setRewardText(rewardText);
        if (!StringUtils.isEmpty(rewardUrl)) {
            item.setRewardUrl(rewardUrl);
            item.setRewardType(rewardType);
        }
        if (id == null) {
            itemMapper.insert(item);
        } else {
            itemMapper.updateById(item);
            // updateById 不更新 null 字段，去掉图片、清空文本、去掉文件要单独置空；同时传了新的以新的为准
            if (Boolean.TRUE.equals(removeImage) && StringUtils.isEmpty(image)) {
                itemMapper.update(null, new UpdateWrapper<MarketItem>().eq("id", id).set("image", null));
                item.setImage(null);
            }
            if (clearText) {
                itemMapper.update(null, new UpdateWrapper<MarketItem>().eq("id", id).set("reward_text", null));
                item.setRewardText(null);
            }
            if (Boolean.TRUE.equals(removeRewardFile) && StringUtils.isEmpty(rewardUrl)) {
                itemMapper.update(null, new UpdateWrapper<MarketItem>().eq("id", id).set("reward_url", null).set("reward_type", null));
                item.setRewardUrl(null);
                item.setRewardType(null);
            }
        }
        Map<Integer, User> sellers = new HashMap<>();
        sellers.put(seller.getId(), seller);
        return itemMap(item, sellers, seller.getId(), true);
    }

    @Override
    public void setOnSale(Integer sellerId, Integer id, boolean onSale) {
        MarketItem item = itemMapper.selectById(id);
        ApiAssert.isTrue(item != null && item.getSellerId().equals(sellerId), "商品不存在");
        ApiAssert.isTrue(item.getStatus() != MarketItem.OFF_BY_ADMIN, "这个商品被管理员下架了，不能自己重新上架");
        itemMapper.update(null, new UpdateWrapper<MarketItem>().eq("id", id)
                .set("status", onSale ? MarketItem.ON_SALE : MarketItem.OFF_BY_SELLER).set("update_time", new Date()));
    }

    // ---------------------------------------------------------------- 用户端：购买

    @Override
    public Map<String, Object> buy(User buyer, Integer itemId) {
        MarketItem item = itemMapper.selectById(itemId);
        ApiAssert.isTrue(item != null && item.getStatus() == MarketItem.ON_SALE, "商品不存在或已下架");
        ApiAssert.isTrue(!item.getSellerId().equals(buyer.getId()), "不能买自己上架的商品");

        // 全部在一个事务里：占库存 → 扣买家积分（不够就整体回滚）→ 加到卖家 → 记订单
        ApiAssert.isTrue(itemMapper.reserve(item.getId()) == 1, "这个商品已经卖完了，或者刚被下架");
        ApiAssert.isTrue(userMapper.deductScore(buyer.getId(), item.getScore()) == 1, "积分不够，还差一点点");
        userMapper.addScore(item.getSellerId(), item.getScore());

        MarketOrder order = new MarketOrder();
        order.setItemId(item.getId());
        order.setBuyerId(buyer.getId());
        order.setSellerId(item.getSellerId());
        order.setItemName(item.getName());
        order.setItemImage(item.getImage());
        order.setScore(item.getScore());
        // 快照：卖家以后改价、改内容、下架，已经买到的人看到的不变
        order.setRewardText(item.getRewardText());
        order.setRewardType(item.getRewardType());
        order.setRewardUrl(item.getRewardUrl());
        order.setInTime(new Date());
        orderMapper.insert(order);

        refreshUserCache(item.getSellerId());
        User latest = userService.selectByIdWithoutCache(buyer.getId());
        SpringContextUtil.getBean(UserService.class).delRedisUser(latest);

        Map<String, Object> map = new HashMap<>();
        map.put("orderId", order.getId());
        map.put("cost", item.getScore());
        map.put("totalScore", latest.getScore());
        map.put("reward", reward(item.getRewardText(), item.getRewardType(), item.getRewardUrl()));
        return map;
    }

    @Override
    public List<Map<String, Object>> myPurchases(Integer buyerId) {
        QueryWrapper<MarketOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("buyer_id", buyerId).orderByDesc("id").last("limit 100");
        List<MarketOrder> orders = orderMapper.selectList(wrapper);
        Map<Integer, User> sellers = usersById(orders.stream().map(MarketOrder::getSellerId).collect(Collectors.toSet()));
        return orders.stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("itemId", o.getItemId());
            m.put("itemName", o.getItemName());
            m.put("itemImage", o.getItemImage());
            m.put("score", o.getScore());
            User seller = sellers.get(o.getSellerId());
            m.put("sellerName", seller == null ? null : seller.getUsername());
            m.put("reward", reward(o.getRewardText(), o.getRewardType(), o.getRewardUrl()));
            m.put("inTime", o.getInTime());
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> mySales(Integer sellerId) {
        QueryWrapper<MarketOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("seller_id", sellerId).orderByDesc("id").last("limit 100");
        List<MarketOrder> orders = orderMapper.selectList(wrapper);
        Map<Integer, User> buyers = usersById(orders.stream().map(MarketOrder::getBuyerId).collect(Collectors.toSet()));
        return orders.stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("itemName", o.getItemName());
            m.put("income", o.getScore());
            User buyer = buyers.get(o.getBuyerId());
            m.put("buyerName", buyer == null ? null : buyer.getUsername());
            m.put("inTime", o.getInTime());
            return m;
        }).collect(Collectors.toList());
    }

    // ---------------------------------------------------------------- 后台

    @Override
    public MyPage<Map<String, Object>> adminItems(Integer pageNo, Integer status) {
        int size = Integer.parseInt(systemConfigService.selectAllConfig().get("page_size"));
        MyPage<MarketItem> page = new MyPage<>(pageNo, size);
        QueryWrapper<MarketItem> wrapper = new QueryWrapper<>();
        if (status != null) wrapper.eq("status", status);
        wrapper.orderByDesc("id");
        itemMapper.selectPage(page, wrapper);
        Set<Integer> ids = page.getRecords().stream().map(MarketItem::getSellerId).collect(Collectors.toSet());
        Map<Integer, User> sellers = usersById(ids);
        MyPage<Map<String, Object>> result = new MyPage<>(page.getCurrent(), page.getSize(), page.getTotal());
        // 后台带上内容，方便审核有没有违规
        result.setRecords(page.getRecords().stream().map(i -> itemMap(i, sellers, null, true)).collect(Collectors.toList()));
        return result;
    }

    @Override
    public void adminSetRemoved(Integer id, boolean removed) {
        MarketItem item = itemMapper.selectById(id);
        ApiAssert.notNull(item, "商品不存在");
        itemMapper.update(null, new UpdateWrapper<MarketItem>().eq("id", id)
                .set("status", removed ? MarketItem.OFF_BY_ADMIN : MarketItem.OFF_BY_SELLER).set("update_time", new Date()));
    }
}
