package co.yiiu.pybbs.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

import java.io.Serializable;
import java.util.Date;

/** 积分兑换商品 */
public class ExchangeProduct implements Serializable {
    private static final long serialVersionUID = 6350019452113287711L;
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private String image;
    private String description;
    // 兑换需要的积分
    private Integer score;
    // 库存，-1 表示不限量
    private Integer stock;
    // 数字大的排前面
    private Integer sort;
    private Boolean enabled;
    // 兑换后给用户看的内容：文本、文件（图片/视频）
    private String rewardText;
    private String rewardType;
    private String rewardUrl;
    private Date inTime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Date getInTime() { return inTime; }
    public void setInTime(Date inTime) { this.inTime = inTime; }

    public String getRewardText() { return rewardText; }
    public void setRewardText(String rewardText) { this.rewardText = rewardText; }
    public String getRewardType() { return rewardType; }
    public void setRewardType(String rewardType) { this.rewardType = rewardType; }
    public String getRewardUrl() { return rewardUrl; }
    public void setRewardUrl(String rewardUrl) { this.rewardUrl = rewardUrl; }
}
