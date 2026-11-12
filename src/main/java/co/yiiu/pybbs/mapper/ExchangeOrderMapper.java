package co.yiiu.pybbs.mapper;

import co.yiiu.pybbs.model.ExchangeOrder;
import co.yiiu.pybbs.util.MyPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

public interface ExchangeOrderMapper extends BaseMapper<ExchangeOrder> {

    /** 后台列表：带上用户名，可按状态、用户名筛选 */
    MyPage<Map<String, Object>> selectAllForAdmin(MyPage<Map<String, Object>> iPage, @Param("status") Integer status,
                                                  @Param("username") String username);

    /** 只有待处理的才能变成已处理（原子操作，防止重复处理） */
    @Update("update exchange_order set status = 1, remark = #{remark}, update_time = now() where id = #{id} and status = 0")
    int markDone(@Param("id") Integer id, @Param("remark") String remark);

    /** 只有待处理的才能取消（原子操作，防止重复取消、重复退积分） */
    @Update("update exchange_order set status = 2, remark = #{remark}, update_time = now() where id = #{id} and status = 0")
    int markCanceled(@Param("id") Integer id, @Param("remark") String remark);
}
