package co.yiiu.pybbs.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

import java.io.Serializable;
import java.util.Date;

/** 积分兑换记录（商品名称、图片、积分是兑换时的快照） */
public class ExchangeOrder implements Serializable {
    private static final long serialVersionUID = 4710293847561029384L;
    public static final int PENDING = 0;
    public static final int DONE = 1;
    public static final int CANCELED = 2;

    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer userId;
    private Integer productId;
    private String productName;
    private String productImage;
    private Integer score;
    // 已经不再使用（不再收集联系方式），列保留着以免丢老数据
    private String contact;
    private Integer status;
    private String remark;
    // 兑换后给用户看的内容：文本、文件（图片/视频）
    private String rewardText;
    private String rewardType;
    private String rewardUrl;
    private Date inTime;
    private Date updateTime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getProductImage() { return productImage; }
    public void setProductImage(String productImage) { this.productImage = productImage; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Date getInTime() { return inTime; }
    public void setInTime(Date inTime) { this.inTime = inTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public String getRewardText() { return rewardText; }
    public void setRewardText(String rewardText) { this.rewardText = rewardText; }
    public String getRewardType() { return rewardType; }
    public void setRewardType(String rewardType) { this.rewardType = rewardType; }
    public String getRewardUrl() { return rewardUrl; }
    public void setRewardUrl(String rewardUrl) { this.rewardUrl = rewardUrl; }
}
