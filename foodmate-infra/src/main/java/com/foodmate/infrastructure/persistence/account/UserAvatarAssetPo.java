package com.foodmate.infrastructure.persistence.account;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foodmate.infrastructure.persistence.BasePo;

/** 用户头像的对象存储元数据，图片字节保留在 PostgreSQL 之外。 */
@TableName("user_avatar_assets")
public class UserAvatarAssetPo extends BasePo {
    @TableId("avatar_asset_id")
    public Long avatarAssetId;

    public Long userId;
    public String storageKey;
    public String url;
    public String mimeType;
    public Long sizeBytes;
    public Integer width;
    public Integer height;
    public String status;
    public String originalFilename;
    public String contentSha256;
}
