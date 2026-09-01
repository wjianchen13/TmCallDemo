# 测试腾讯语音例子
1v1
语聊

# 方案
所有工作重新开始
由于CallCoreView会把大视频和小视频弹窗放在一起，移动小窗可能会被上层的UI遮挡，所以这种方案不合适
https://cloud.tencent.com/document/product/647/128196
所以采用自定义UI的方式，下面文档中的方案二
https://cloud.tencent.com/document/product/647/78739

语聊采用这个方式，TRTC
https://cloud.tencent.com/document/product/647/116545

需要使用自定义信令，所以不能使用TUICallKit
https://cloud.tencent.com/document/product/1640/81147




# 文档
快速接入
https://cloud.tencent.com/document/product/647/78729

无UI接入，拨打第一通电话
https://cloud.tencent.com/document/product/647/128196



