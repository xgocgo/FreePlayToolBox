# 公告服务部署（Cloudflare Worker）

部署完你会得到一个地址，比如 `https://toolbox-ann.yourname.workers.dev`。
打开它就是一个**网页发布后台**，填标题正文点发布，用户打开 App 立刻能看到。

全程免费，不需要域名、不需要服务器。

---

## 一、注册

1. 打开 https://dash.cloudflare.com/sign-up
2. 用邮箱注册（不用绑卡）

## 二、创建 KV 存储

1. 左侧菜单 → **Storage & Databases** → **KV**
2. 点 **Create a namespace**，名称填 `announcements`
3. 创建后记下这个命名空间的 **ID**（一长串字符）

## 三、创建 Worker

1. 左侧菜单 → **Workers & Pages** → **Create** → **Worker**
2. 名称填 `toolbox-ann`，点 **Deploy**
3. 部署后点 **Edit code**
4. 把本目录 `worker.js` 里的**全部内容**粘贴进去，覆盖原有代码
5. 右上角 **Deploy** 保存

## 四、绑定 KV 和口令

回到 Worker 页面 → **Settings** → **Variables and Bindings**

**添加 KV 绑定：**
- 点 **Add** → **KV namespace**
- Variable name 填 `ANN`（必须一字不差）
- KV namespace 选刚才建的 `announcements`

**添加环境变量：**
- 点 **Add** → **Text**
- Variable name 填 `ADMIN_KEY`
- Value 填你自己想的口令，比如 `my-secret-2026`

**重要：ADMIN_KEY 设完点 Encrypt 或保存，别泄露。** 知道这个口令的人就能发公告。

改完点 **Deploy** 生效。

## 五、测试

浏览器打开：

```
https://toolbox-ann.你的用户名.workers.dev
```

能看到「公告后台」页面 = 部署成功。

填口令 → 写标题正文 → 添加到列表 → 发布到线上。
然后再打开一次页面，公告应该还在（说明 KV 写入成功）。

## 六、接到 App 上

1. 打开玩机工具箱
2. 设置 → 安全与隐私 → **公告服务地址**
3. 粘贴你的 Worker 地址，例如：
   ```
   https://toolbox-ann.yourname.workers.dev
   ```
4. 保存

之后 App 会优先从这个地址拉公告，拉不到才回落到 GitHub 文件。

---

## 常见问题

**Q：口令忘了怎么办？**
Worker → Settings → Variables → 改 ADMIN_KEY 的值，重新 Deploy。

**Q：发布后 App 看不到？**
- 检查 Worker 地址填对没有（末尾不要带 `/`）
- 点「发布到线上」后要有绿色提示
- App 里下拉首页刷新，或杀掉重开
- 公告 id 重复的话用户端会去重，换新 id 才会再次弹出

**Q：图片怎么放？**
后台填图片链接。建议把图片传到 GitHub 仓库再用直链，
或者用 App 里的公告编辑器（从相册选图后会压缩内嵌进 JSON）。

**Q：免费额度够吗？**
Workers 免费版每天 10 万次请求，个人使用远远够。

**Q：能不能不给别人看后台页面？**
后台页面本身不返回数据，只有 POST 才需要口令，GET 页面只是空表单。
别人打开也只是看到表单，没有口令发不了公告。
