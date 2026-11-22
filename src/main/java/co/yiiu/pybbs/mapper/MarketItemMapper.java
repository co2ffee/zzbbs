package co.yiiu.pybbs.mapper;

import co.yiiu.pybbs.model.MarketItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface MarketItemMapper extends BaseMapper<MarketItem> {

    /**
     * 成交时占一份：商品必须还在上架、库存够（-1 不限量不减库存），同时已卖数 +1。
     * 一条 SQL 原子完成，并发购买不会超卖，也不会买到刚下架的商品；返回 0 表示买不了。
     */
    @Update("update market_item set sold = sold + 1, stock = case when stock > 0 then stock - 1 else stock end "
            + "where id = #{id} and status = 0 and (stock > 0 or stock < 0)")
    int reserve(@Param("id") Integer id);
}
