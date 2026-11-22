package co.yiiu.pybbs.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

import java.io.Serializable;
import java.util.Date;

/** 用户集市商品：卖家自己定价 */
public class MarketItem implements Serializable {
    private static final long serialVersionUID = 4120058823419055071L;
    public static final int ON_SALE = 0;
    public static final int OFF_BY_SELLER = 1;
    public static final int OFF_BY_ADMIN = 2;

    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer sellerId;
    private String name;
    private String image;
    private String description;
    private Integer score;
    // -1 不限量
    private Integer stock;
    private Integer sold;
    private Integer status;
    // 付款后才给买家看的内容
    private String rewardText;
    private String rewardType;
    private String rewardUrl;
    private Date inTime;
    private Date updateTime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getSellerId() { return sellerId; }
    public void setSellerId(Integer sellerId) { this.sellerId = sellerId; }
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
    public Integer getSold() { return sold; }
    public void setSold(Integer sold) { this.sold = sold; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getRewardText() { return rewardText; }
    public void setRewardText(String rewardText) { this.rewardText = rewardText; }
    public String getRewardType() { return rewardType; }
    public void setRewardType(String rewardType) { this.rewardType = rewardType; }
    public String getRewardUrl() { return rewardUrl; }
    public void setRewardUrl(String rewardUrl) { this.rewardUrl = rewardUrl; }
    public Date getInTime() { return inTime; }
    public void setInTime(Date inTime) { this.inTime = inTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
