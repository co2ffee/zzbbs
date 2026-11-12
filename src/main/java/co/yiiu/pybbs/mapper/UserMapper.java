package co.yiiu.pybbs.mapper;

import co.yiiu.pybbs.model.User;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;
import java.util.Date;

/**
 * Created by tomoya.
 * Copyright (c) 2018, All Rights Reserved.
 * https://atjiu.github.io
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    int countToday(@Param("start") Date start, @Param("end") Date end);

    /** 扣积分：积分够才扣（原子操作，并发兑换不会把积分扣成负数），返回 0 表示积分不够 */
    @Update("update user set score = score - #{score} where id = #{id} and score >= #{score}")
    int deductScore(@Param("id") Integer id, @Param("score") Integer score);

    /** 加积分（取消兑换时退还） */
    @Update("update user set score = score + #{score} where id = #{id}")
    int addScore(@Param("id") Integer id, @Param("score") Integer score);
}
