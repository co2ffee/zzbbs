package co.yiiu.pybbs.service.impl;

import co.yiiu.pybbs.exception.ApiAssert;
import co.yiiu.pybbs.exception.ApiException;
import co.yiiu.pybbs.mapper.ExchangeOrderMapper;
import co.yiiu.pybbs.mapper.ExchangeProductMapper;
import co.yiiu.pybbs.mapper.UserMapper;
import co.yiiu.pybbs.model.ExchangeOrder;
import co.yiiu.pybbs.model.ExchangeProduct;
import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.service.IExchangeService;
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
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class ExchangeService implements IExchangeService {

    private static final int MAX_SCORE = 10_000_000;
    private static final int MAX_REWARD_TEXT = 5000;

    @Resource
    private ExchangeProductMapper productMapper;
    @Resource
    private ExchangeOrderMapper orderMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private IUserService userService;
    @Resource
    private ISystemConfigService systemConfigService;
    @Resource
    private FileUtil fileUtil;

    // ---------------------------------------------------------------- 用户端

    @Override
    public List<Map<String, Object>> listProducts() {
        QueryWrapper<ExchangeProduct> wrapper = new QueryWrapper<>();
        wrapper.eq("enabled", true).orderByDesc("sort").orderByDesc("id");
        return productMapper.selectList(wrapper).stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("image", p.getImage());
            m.put("description", p.getDescription());
            m.put("score", p.getScore());
            // 不限量的商品不返回具体库存（-1），前端用 unlimited 判断；限量的返回剩余数量
            m.put("unlimited", p.getStock() < 0);
            m.put("stock", p.getStock());
            m.put("soldOut", p.getStock() == 0);
            // 只告诉用户兑换后会得到什么类型的内容，内容本身（文本、文件地址）兑换之后才给
            List<String> delivers = new java.util.ArrayList<>();
            if (!StringUtils.isEmpty(p.getRewardText())) delivers.add("text");
            if (!StringUtils.isEmpty(p.getRewardUrl())) delivers.add(p.getRewardType());
            m.put("delivers", delivers);
            return m;
        }).collect(Collectors.toList());
    }

    /** 兑换到的内容：文本、文件类型（image / video）、文件地址；都没有就是 null */
    private Map<String, Object> reward(String text, String type, String url) {
        if (StringUtils.isEmpty(text) && StringUtils.isEmpty(url)) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", StringUtils.isEmpty(text) ? null : text);
        m.put("type", StringUtils.isEmpty(url) ? null : type);
        m.put("url", StringUtils.isEmpty(url) ? null : url);
        return m;
    }

    @Override
    public Map<String, Object> exchange(User user, Integer productId) {
        ExchangeProduct product = productMapper.selectById(productId);
        ApiAssert.isTrue(product != null && Boolean.TRUE.equals(product.getEnabled()), "商品不存在或已下架");

        // 先减库存再扣积分，任何一步失败都抛异常，整个事务回滚（库存、积分都不会少）
        if (product.getStock() >= 0) {
            ApiAssert.isTrue(productMapper.decreaseStock(product.getId()) == 1, "这个商品已经兑完了");
        }
        ApiAssert.isTrue(userMapper.deductScore(user.getId(), product.getScore()) == 1, "积分不够，还差一点点");

        ExchangeOrder order = new ExchangeOrder();
        order.setUserId(user.getId());
        order.setProductId(product.getId());
        order.setProductName(product.getName());
        order.setProductImage(product.getImage());
        order.setScore(product.getScore());
        // 兑换时的内容快照：以后商品改了、删了，已经兑换的用户看到的还是兑换时的
        order.setRewardText(product.getRewardText());
        order.setRewardType(product.getRewardType());
        order.setRewardUrl(product.getRewardUrl());
        order.setStatus(ExchangeOrder.DONE); // 自动发放，不需要管理员再处理
        order.setInTime(new Date());
        orderMapper.insert(order);

        User latest = userService.selectByIdWithoutCache(user.getId());
        // 积分是 SQL 直接改的，通知一下缓存插件（没装插件时是空方法）
        SpringContextUtil.getBean(UserService.class).delRedisUser(latest);

        Map<String, Object> map = new HashMap<>();
        map.put("orderId", order.getId());
        map.put("cost", product.getScore());
        map.put("totalScore", latest.getScore());
        map.put("reward", reward(product.getRewardText(), product.getRewardType(), product.getRewardUrl()));
        return map;
    }

    @Override
    public List<Map<String, Object>> myOrders(Integer userId) {
        QueryWrapper<ExchangeOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("id").last("limit 100");
        return orderMapper.selectList(wrapper).stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("productName", o.getProductName());
            m.put("productImage", o.getProductImage());
            m.put("score", o.getScore());
            m.put("status", o.getStatus());
            // 已取消（积分退了）的不再给内容
            m.put("reward", o.getStatus() != null && o.getStatus() == ExchangeOrder.CANCELED
                    ? null : reward(o.getRewardText(), o.getRewardType(), o.getRewardUrl()));
            m.put("remark", o.getRemark());
            m.put("inTime", o.getInTime());
            return m;
        }).collect(Collectors.toList());
    }

    // ---------------------------------------------------------------- 后台

    private int pageSize() {
        return Integer.parseInt(systemConfigService.selectAllConfig().get("page_size"));
    }

    @Override
    public MyPage<ExchangeProduct> selectProducts(Integer pageNo) {
        MyPage<ExchangeProduct> page = new MyPage<>(pageNo, pageSize());
        QueryWrapper<ExchangeProduct> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("sort").orderByDesc("id");
        productMapper.selectPage(page, wrapper);
        return page;
    }

    @Override
    public ExchangeProduct selectProduct(Integer id) {
        return productMapper.selectById(id);
    }

    @Override
    public ExchangeProduct saveProduct(Integer id, String name, String image, String description, Integer score,
                                       Integer stock, Integer sort, Boolean enabled, Boolean removeImage,
                                       String rewardText, String rewardType, String rewardUrl, Boolean removeRewardFile) {
        name = name == null ? "" : name.trim();
        ApiAssert.isTrue(name.length() >= 1 && name.length() <= 100, "商品名称要填，最多 100 个字");
        ApiAssert.notNull(score, "请填写兑换需要的积分");
        ApiAssert.isTrue(score >= 1 && score <= MAX_SCORE, "兑换积分要在 1 ~ " + MAX_SCORE + " 之间");
        if (stock == null) stock = -1;
        ApiAssert.isTrue(stock >= -1, "库存不能小于 -1（-1 表示不限量）");
        if (description != null) {
            description = description.trim();
            ApiAssert.isTrue(description.length() <= 500, "商品简介最多 500 个字");
        }
        // 图片只允许自己存储域名下的地址（后台直传后拿到的 fileUrl），避免写入任意外部地址
        if (!StringUtils.isEmpty(image)) {
            ApiAssert.isTrue(image.length() <= 500, "图片地址太长");
            ApiAssert.isTrue(fileUtil.isOwnStorageUrl(image), "图片地址不合法");
        }

        // 兑换后给用户看的内容：文本最多 5000 字；文件只能是自己存储域名下的图片/视频
        boolean clearText = false;
        if (rewardText != null) {
            rewardText = rewardText.trim();
            ApiAssert.isTrue(rewardText.length() <= MAX_REWARD_TEXT, "兑换后给用户看的文本最多 " + MAX_REWARD_TEXT + " 个字");
            clearText = rewardText.isEmpty();
        }
        if (!StringUtils.isEmpty(rewardUrl)) {
            ApiAssert.isTrue("image".equals(rewardType) || "video".equals(rewardType), "兑换文件的类型只能是图片或视频");
            ApiAssert.isTrue(rewardUrl.length() <= 500, "兑换文件地址太长");
            ApiAssert.isTrue(fileUtil.isOwnStorageUrl(rewardUrl), "兑换文件地址不合法");
        }

        ExchangeProduct product;
        if (id == null) {
            product = new ExchangeProduct();
            product.setInTime(new Date());
        } else {
            product = productMapper.selectById(id);
            ApiAssert.notNull(product, "商品不存在");
        }
        product.setName(name);
        // 没传新图片：保持原来的图片不变；传了空字符串之外的值才更新
        if (!StringUtils.isEmpty(image)) product.setImage(image);
        product.setDescription(StringUtils.isEmpty(description) ? null : description);
        product.setScore(score);
        product.setStock(stock);
        product.setSort(sort == null ? 0 : sort);
        product.setEnabled(enabled == null ? Boolean.TRUE : enabled);
        if (rewardText != null && !clearText) product.setRewardText(rewardText);
        if (!StringUtils.isEmpty(rewardUrl)) {
            product.setRewardUrl(rewardUrl);
            product.setRewardType(rewardType);
        }
        if (id == null) {
            productMapper.insert(product);
        } else {
            productMapper.updateById(product);
            // 去掉图片：updateById 默认不更新 null 字段，要单独把 image 置空。同时传了新图片就以新图片为准
            if (Boolean.TRUE.equals(removeImage) && StringUtils.isEmpty(image)) {
                productMapper.update(null, new UpdateWrapper<ExchangeProduct>().eq("id", id).set("image", null));
                product.setImage(null);
            }
            // 同样的原因：清空文本、去掉兑换文件要单独置空；同时传了新文件以新文件为准
            if (clearText) {
                productMapper.update(null, new UpdateWrapper<ExchangeProduct>().eq("id", id).set("reward_text", null));
                product.setRewardText(null);
            }
            if (Boolean.TRUE.equals(removeRewardFile) && StringUtils.isEmpty(rewardUrl)) {
                productMapper.update(null, new UpdateWrapper<ExchangeProduct>().eq("id", id).set("reward_url", null).set("reward_type", null));
                product.setRewardUrl(null);
                product.setRewardType(null);
            }
        }
        return product;
    }

    @Override
    public void deleteProduct(Integer id) {
        // 兑换记录里有商品名称、图片、积分的快照，删商品不影响历史记录
        productMapper.deleteById(id);
    }

    @Override
    public MyPage<Map<String, Object>> selectOrders(Integer pageNo, Integer status, String username) {
        MyPage<Map<String, Object>> page = new MyPage<>(pageNo, pageSize());
        return orderMapper.selectAllForAdmin(page, status, StringUtils.isEmpty(username) ? null : username);
    }

    private String cleanRemark(String remark) {
        if (remark == null) return null;
        remark = remark.trim();
        ApiAssert.isTrue(remark.length() <= 500, "备注最多 500 个字");
        return remark.isEmpty() ? null : remark;
    }

    @Override
    public void cancel(Integer id, String remark) {
        ExchangeOrder order = orderMapper.selectById(id);
        ApiAssert.notNull(order, "兑换记录不存在");
        // 先把状态改成“已取消”（原子，已取消的不能再取消），成功了才退积分、加库存：重复点取消不会重复退积分
        ApiAssert.isTrue(orderMapper.markCanceled(id, cleanRemark(remark)) == 1, "这条兑换记录已经取消过了");
        userMapper.addScore(order.getUserId(), order.getScore());
        productMapper.increaseStock(order.getProductId());
        User user = userService.selectByIdWithoutCache(order.getUserId());
        if (user != null) SpringContextUtil.getBean(UserService.class).delRedisUser(user);
    }
}
