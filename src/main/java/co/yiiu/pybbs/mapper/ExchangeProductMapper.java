package co.yiiu.pybbs.mapper;

import co.yiiu.pybbs.model.ExchangeProduct;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ExchangeProductMapper extends BaseMapper<ExchangeProduct> {

    /** 库存 -1：只有库存大于 0 才能减，返回 0 表示已经兑完（原子操作，并发不会超卖） */
    @Update("update exchange_product set stock = stock - 1 where id = #{id} and stock > 0")
    int decreaseStock(@Param("id") Integer id);

    /** 取消兑换时把库存加回去（不限量的商品 stock = -1，不动） */
    @Update("update exchange_product set stock = stock + 1 where id = #{id} and stock >= 0")
    int increaseStock(@Param("id") Integer id);
}
