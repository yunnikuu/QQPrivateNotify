# QQPrivateNotify · QQ通知过滤模块

这是一个LSP模块 使用 **libxposed API 101 构建**

## 当前功能

- 打开或关闭私聊消息通知
- 打开或关闭群聊消息通知
- 功能保存实时生效 无需重启作用域应用

## 构建

在项目目录使用**powershell**运行：

```powershell
.\build.cmd
```

Debug APK 输出目录 `app/build/outputs/apk/debug/app-debug.apk`    

## LSPosed 配置
- **支持 API 100 + 版本**  
- 在 LSPosed 中启用模块并只勾选 `com.tencent.mobileqq`  
- 强行停止作用域应用再重新启动即生效  

## 适配

仅在安卓16 QQ**9.3.50**测试正常  

其余版本请自行测试 （理论大部分版本都兼容）  

## 注意事项

请确保进程 `com.tencent.mobileqq:MSF`一直保持在后台  

请确保QQ主进程 `com.tencent.mobileqq`在后台运行  
 ***后台墓碑是否影响请自行测试**  

## 部分问题

### 时不时漏出几条群消息（偶尔几条）
- 请确保进程`com.tencent.mobileqq:MSF`没被其他内存管理模块/程序清理 清理后的短暂几秒内会使群聊消息拦截短暂失效  
- 将此进程加入模块白名单即可  

### 大量群消息漏出（模块失效）
- 请确保QQ主进程没有被其他第三方模块/程序清理杀后台


