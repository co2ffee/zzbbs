package co.yiiu.pybbs.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

import java.io.Serializable;
import java.util.Date;

/**
 * 每日签到记录，每人每天一条
 */
public class Checkin implements Serializable {
    private static final long serialVersionUID = 5160133281934421931L;
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer userId;
    // 签到日期 yyyy-MM-dd（按站点时区）；用字符串避免 Date 和时区来回转换导致差一天
    private String checkinDate;
    private Integer score;
    // 连续签到天数（含当天）
    private Integer streak;
    private Date inTime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getCheckinDate() { return checkinDate; }
    public void setCheckinDate(String checkinDate) { this.checkinDate = checkinDate; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Integer getStreak() { return streak; }
    public void setStreak(Integer streak) { this.streak = streak; }
    public Date getInTime() { return inTime; }
    public void setInTime(Date inTime) { this.inTime = inTime; }
}
