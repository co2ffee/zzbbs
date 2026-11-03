package co.yiiu.pybbs.controller.api;

import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.plugin.SocialPlugin;
import co.yiiu.pybbs.service.ISystemConfigService;
import co.yiiu.pybbs.service.ITopicService;
import co.yiiu.pybbs.service.IUserService;
import co.yiiu.pybbs.util.MyPage;
import co.yiiu.pybbs.util.Result;
import co.yiiu.pybbs.util.SensitiveWordUtil;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 前后端分离的前台需要的公共接口：搜索、积分榜、站点信息。
 * 这些原来都是通过 Freemarker 指令（tag_search / score / tag_social_list）在模板里渲染的。
 */
@RestController
@RequestMapping("/api")
public class SiteApiController extends BaseApiController {

    @Resource
    private ITopicService topicService;
    @Resource
    private IUserService userService;
    @Resource
    private ISystemConfigService systemConfigService;
    @Resource
    private SocialPlugin socialPlugin;

    // 搜索话题
    @GetMapping("/search")
    public Result search(@RequestParam(defaultValue = "1") Integer pageNo, @RequestParam String keyword) {
        keyword = Jsoup.clean(keyword, Whitelist.basic()).replace("\"", "").replace("'", "").trim();
        if (StringUtils.isEmpty(keyword)) return error("请输入搜索关键字");
        Integer pageSize = Integer.parseInt(systemConfigService.selectAllConfig().get("page_size"));
        MyPage<Map<String, Object>> page = topicService.search(pageNo, pageSize, keyword);
        for (Map<String, Object> map : page.getRecords()) {
            Object content = map.get("content");
            map.put("content", StringUtils.isEmpty(content) ? null
                    : SensitiveWordUtil.replaceSensitiveWord(content.toString(), "*", SensitiveWordUtil.MinMatchType));
        }
        return success(page);
    }

    // 积分榜，只返回公开信息，不返回邮箱/手机号等
    @GetMapping("/top100")
    public Result top100(@RequestParam(defaultValue = "100") Integer limit) {
        if (limit > 100) limit = 100;
        if (limit < 1) limit = 1;
        List<Map<String, Object>> result = new ArrayList<>();
        for (User user : userService.selectTop(limit)) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", user.getId());
            map.put("username", user.getUsername());
            map.put("avatar", user.getAvatar());
            map.put("score", user.getScore());
            result.add(map);
        }
        return success(result);
    }

    // 站点信息：只返回前端渲染需要的公开配置，不暴露密钥类配置
    @GetMapping("/site")
    public Result site() {
        Map<String, String> config = systemConfigService.selectAllConfig();
        Map<String, Object> data = new LinkedHashMap<>();
        for (String key : Arrays.asList("name", "base_url", "page_size", "content_style",
                "upload_image_size_limit", "upload_video_size_limit", "websocket", "websocket_url")) {
            if (config.get(key) != null) data.put(key, config.get(key));
        }
        data.put("smsEnabled", !StringUtils.isEmpty(config.get("sms_access_key_id")));
        data.put("socials", socialPlugin.getAllAvailableSocialPlatform());
        return success(data);
    }

    // 第三方登录回调后，前端用同一个 session 换取 token（token 不出现在 URL 里）
    @GetMapping("/oauth/session")
    public Result oauthSession(HttpSession session) {
        Object attr = session.getAttribute("_user");
        if (!(attr instanceof User)) return error("未登录");
        User user = userService.selectByIdWithoutCache(((User) attr).getId());
        Map<String, Object> map = new HashMap<>();
        map.put("user", user);
        map.put("token", user.getToken());
        return success(map);
    }
}
