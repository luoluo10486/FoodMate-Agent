package com.foodmate.infrastructure.persistence.account;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foodmate.infrastructure.persistence.BasePo;

/** Object-storage metadata for a user's avatar; image bytes stay outside PostgreSQL. */
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
