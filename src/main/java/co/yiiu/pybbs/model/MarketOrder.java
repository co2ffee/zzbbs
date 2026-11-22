package co.yiiu.pybbs.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

import java.io.Serializable;
import java.util.Date;

/** 用户集市订单：成交时商品的快照 */
public class MarketOrder implements Serializable {
    private static final long serialVersionUID = 7790318214450312345L;
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer itemId;
    private Integer buyerId;
    private Integer sellerId;
    private String itemName;
    private String itemImage;
    private Integer score;
    private String rewardText;
    private String rewardType;
    private String rewardUrl;
    private Date inTime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getItemId() { return itemId; }
    public void setItemId(Integer itemId) { this.itemId = itemId; }
    public Integer getBuyerId() { return buyerId; }
    public void setBuyerId(Integer buyerId) { this.buyerId = buyerId; }
    public Integer getSellerId() { return sellerId; }
    public void setSellerId(Integer sellerId) { this.sellerId = sellerId; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getItemImage() { return itemImage; }
    public void setItemImage(String itemImage) { this.itemImage = itemImage; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getRewardText() { return rewardText; }
    public void setRewardText(String rewardText) { this.rewardText = rewardText; }
    public String getRewardType() { return rewardType; }
    public void setRewardType(String rewardType) { this.rewardType = rewardType; }
    public String getRewardUrl() { return rewardUrl; }
    public void setRewardUrl(String rewardUrl) { this.rewardUrl = rewardUrl; }
    public Date getInTime() { return inTime; }
    public void setInTime(Date inTime) { this.inTime = inTime; }
}
