package co.yiiu.pybbs.controller.admin;

import co.yiiu.pybbs.exception.ApiAssert;
import co.yiiu.pybbs.model.AdminUser;
import co.yiiu.pybbs.service.IAdminUserService;
import co.yiiu.pybbs.service.ICommentService;
import co.yiiu.pybbs.service.ITagService;
import co.yiiu.pybbs.service.ITopicService;
import co.yiiu.pybbs.service.IUserService;
import co.yiiu.pybbs.util.Result;
import co.yiiu.pybbs.util.bcrypt.BCryptPasswordEncoder;
import com.sun.management.OperatingSystemMXBean;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by tomoya.
 * Copyright (c) 2018, All Rights Reserved.
 * https://atjiu.github.io
 */
@Controller
public class IndexAdminController extends BaseAdminController {

    private final Logger log = LoggerFactory.getLogger(IndexAdminController.class);

    @Resource
    private ITopicService topicService;
    @Resource
    private ITagService tagService;
    @Resource
    private ICommentService commentService;
    @Resource
    private IUserService userService;
    @Resource
    private IAdminUserService adminUserService;

    @RequiresUser
    @GetMapping({"/admin", "/admin/", "/admin/index"})
    @ResponseBody
    public Result index() {
        Map<String, Object> data = new HashMap<>();
        // 查询当天新增话题
        data.put("topic_count", topicService.countToday());
        // 查询当天新增标签
        data.put("tag_count", tagService.countToday());
        // 查询当天新增评论
        data.put("comment_count", commentService.countToday());
        // 查询当天新增用户
        data.put("user_count", userService.countToday());

        // 获取操作系统的名字
        data.put("os_name", System.getProperty("os.name"));

        // 内存
        int kb = 1024;
        OperatingSystemMXBean osmxb = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        // 总的物理内存
        long totalMemorySize = osmxb.getTotalPhysicalMemorySize() / kb;
        //已使用的物理内存
        long usedMemory = (osmxb.getTotalPhysicalMemorySize() - osmxb.getFreePhysicalMemorySize()) / kb;
        // 获取系统cpu负载
        double systemCpuLoad = osmxb.getSystemCpuLoad();
        // 获取jvm线程负载
        double processCpuLoad = osmxb.getProcessCpuLoad();

        data.put("totalMemorySize", totalMemorySize);
        data.put("usedMemory", usedMemory);
        data.put("systemCpuLoad", systemCpuLoad);
        data.put("processCpuLoad", processCpuLoad);

        return success(data);
    }

    // 老的后台登录页地址，管理端 SPA 迁到 /admin-ui/ 后跳转过去，保持原来的链接可用
    @GetMapping("/adminlogin")
    public String adminlogin() {
        return redirect("/admin-ui/login");
    }

    // 处理后台登录逻辑（新：供token鉴权的接口消费方使用，走BCrypt手动校验，不依赖Shiro session）
    @PostMapping("/admin/api/login")
    @ResponseBody
    public Result apiLogin(String username, String password, String code, HttpSession session) {
        String captcha = (String) session.getAttribute("_captcha");
        ApiAssert.notTrue(captcha == null || StringUtils.isEmpty(code) || !captcha.equalsIgnoreCase(code), "验证码不正确");
        AdminUser adminUser = adminUserService.selectByUsername(username);
        ApiAssert.notNull(adminUser, "用户不存在");
        ApiAssert.isTrue(new BCryptPasswordEncoder().matches(password, adminUser.getPassword()), "用户名或密码不正确");
        session.removeAttribute("_captcha");
        if (StringUtils.isEmpty(adminUser.getToken())) {
            adminUser.setToken(adminUserService.generateToken());
            adminUserService.update(adminUser);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("adminUser", adminUser);
        map.put("token", adminUser.getToken());
        return success(map);
    }

    @GetMapping("/admin/logout")
    public String logout() {
        SecurityUtils.getSubject().logout();
        return redirect("/admin-ui/login");
    }

    @GetMapping("/admin/no_auth")
    public String noAuth() {
        return redirect("/admin-ui/login");
    }
}
