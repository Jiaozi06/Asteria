# Asteria

Asteria 是支持多渠道接入的原生 Android AI 聊天客户端。聊天记录保存在本机，可导入 DeepSeek 对话，并按模型设置上下文窗口。

## 安装与配置

1. 下载 `Asteria.apk` 并安装，支持 Android 8.0 及以上。[GitHub Releases](https://github.com/Jiaozi06/Asteria/releases)
2. 从对话侧栏进入「设置 → 连接与数据 → API 设置」，选择模型提供商，确认接口地址和调用协议，按需填写 API Key。
3. 从上游模型列表选择模型，或手动填写模型 ID，再点「测试并保存」。

| 提供商 | 默认 API 地址 | 自动模式使用的协议 |
| --- | --- | --- |
| DeepSeek | `https://api.deepseek.com` | Anthropic Messages |
| OpenAI | `https://api.openai.com/v1` | OpenAI Chat Completions |
| Anthropic | `https://api.anthropic.com` | Anthropic Messages |
| Gemini | `https://generativelanguage.googleapis.com` | Gemini GenerateContent |
| Ollama | `http://127.0.0.1:11434` | Ollama 原生 |
| 自定义提供商 | 手动填写 | OpenAI Chat Completions |

每个提供商分别保存地址、模型和协议。可手动切换协议，Asteria 会用所选协议读取模型列表和流式回复。DeepSeek 改用其他地址时，自动模式沿用 OpenAI 兼容方式。连接电脑上的 Ollama 时，填入电脑的局域网地址和端口，例如 `http://192.168.1.10:11434`。

## 聊天与模型设置

- **流式回复和 Markdown。** 支持代码高亮、独立复制按钮和可横向滚动的代码块与表格。接口返回的思考过程会在生成时展开、回复结束后收起，点标题可重新查看。点按自己的消息可编辑重发。
- **单对话设置。** 顶部齿轮可设置回复语气、自定义指令和最大输出 Token。推理强度、采样参数和输出格式随协议提供。DeepSeek 官方模式支持 Low、High、Max，应用也会随该模式的请求提供设备日期。
- **上下文窗口。** 在「API 设置 → 模型上下文窗口」以 Token 为单位填写，按提供商、接口、协议和模型保存。自动压缩默认开启，会预留输出空间，压缩较早内容并保留最近轮次。接口报告上下文超限时可缩减历史重试，本地记录保留全文。Ollama 原生协议还会发送 `num_ctx`。
- **附件、搜索和语音。** DeepSeek 官方地址使用 Anthropic 协议时，可拍照、选图、添加文件、联网搜索及使用 DeepSeek 语音转写。发送前可查看图片缩略图和文件名。联网搜索和含附件的对话使用 Flash，Flash 也可以阅读网页链接。网页来源可在回复里打开。其他协议可使用设备的语音识别服务，朗读使用系统文字转语音。[DeepSeek 开放平台 API 文档](https://api-docs.deepseek.com/guides/build_a_chatbot/)

## 对话历史与外观

- **历史管理。** 侧栏按日期分组，支持全文搜索、重命名、置顶和删除。进入多选后，长按并滑动可批量勾选或取消，滑到边缘会自动滚动。会话正文按需读取，全文搜索在后台执行。
- **导入与清空。** 在「设置 → 连接与数据 → 导入 DeepSeek 对话」粘贴官方导出链接，或选择第 3 版 ZIP。导入追加当前分支的消息、思考、网页来源和附件文件名，已有对话会保留，重复会话会跳过。同一设置组的「清空全部对话」会在确认后清空本地记录。
- **记忆星云。** 在「设置 → 连接与数据 → 记忆星云」按对话最后更新时间选择最近 1 天、7 天、30 天或全部。整理 30 天和全部前会提示 Token 消耗并要求确认。自动总结默认关闭。应用通过当前配置的 API 概括你发出的部分文字。星云中会轮流浮现部分记忆点，点按可查看对应摘要，下方列表支持分类筛选。摘要只保存在本机，不参与普通聊天。星云与对话历史独立，可以单独删除或清空。
- **显示和输入。** 可切换浅色、深色或跟随系统，并在设置里预览和调整聊天字号。长按空输入框可录音，松手转写到草稿，上滑取消。应用会记住文字或语音输入模式。
- **实验性功能。** 在「设置 → 应用 → 实验性功能」可分别开启 Android 14 及以上的应用内预测性返回，以及 Android 12 及以上的弹窗毛玻璃。毛玻璃还需要设备支持窗口模糊。

## 密钥、代理与数据

- **密钥管理。** 同一接口可保存多把 Key，普通对话可轮询或随机使用，含附件的对话沿用附件密钥。密钥经 Android Keystore 加密保存，列表仅展示首尾片段。DeepSeek 官方接口的余额显示在对话页脚右侧，多把 Key 按币种合计。同一账号的多把 Key 可能重复计入同一笔余额。
- **SOCKS5 代理。** 可按提供商启用代理，并按需填写用户名和密码。代理凭据加密保存。
- **请求日志。** 在「设置 → 连接与数据 → 记录请求日志」开启后，可查看请求正文、响应状态和流式数据。API Key 会脱敏，日志只保留在本次运行中，关闭开关时清除。
- **本地记录与上游数据。** 聊天记录存于应用私有目录，模型回复由用户配置的上游接口生成。DeepSeek 官方模式的搜索、附件和语音转写会将相关内容发送到 DeepSeek。删除本地对话后，已上传的附件仍存于开放平台的文件列表。

## 更新与源码构建

应用启动时自动检查更新，也可在「设置 → 关于 → 检查更新」手动查询。发现新版后会展示更新说明，并提供 APK 下载入口。系统地区设为中国大陆时会使用 HTTPS 镜像。

在 Windows PowerShell 使用 JDK 17、Android SDK Platform 35 和 Build Tools 35.0.0 构建。

```powershell
.\build.ps1 -SdkRoot 'C:\Android\Sdk' -JavaHome 'C:\Java\jdk-17'
```

脚本下载并校验 CommonMark Java 0.32.0 及其扩展，使用本机签名生成 `build/Asteria.apk`。项目及第三方许可证随 APK 附带。

## 项目许可证

Asteria 公开源码，采用 [PolyForm Noncommercial 1.0.0](LICENSE)。可按许可证条款进行非商业使用、修改和分发。商业用途需要权利人另行授权。

此前以 MIT 许可证发布的版本继续适用各自发布时所附的许可证。第三方依赖遵循各自的许可证。[依赖许可证](src/main/assets/)

新建对话和导入图标来自 DeepSeek Chat Demo，采用 MIT 许可证。[图标源码](https://github.com/deepseek-ai/chat-demo/blob/cdf9617c5f265d58e0e1805635b2c10a3d41284d/src/components/base/icons.tsx) · [MIT 许可证](src/main/assets/deepseek-chat-demo-LICENSE.txt)
