package co.yiiu.pybbs.controller.admin;

import co.yiiu.pybbs.model.AdminUser;
import co.yiiu.pybbs.service.IAdminUserService;
import co.yiiu.pybbs.service.IRoleService;
import co.yiiu.pybbs.util.bcrypt.BCryptPasswordEncoder;
import co.yiiu.pybbs.util.Result;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by tomoya.
 * Copyright (c) 2018, All Rights Reserved.
 * https://atjiu.github.io
 */
@Controller
@RequestMapping("/admin/admin_user")
public class AdminUserAdminController extends BaseAdminController {

    @Resource
    private IAdminUserService adminUserService;
    @Resource
    private IRoleService roleService;

    @RequiresPermissions("admin_user:list")
    @GetMapping("/list")
    @ResponseBody
    public Result list() {
        return success(adminUserService.selectAll());
    }

    @RequiresPermissions("admin_user:add")
    @GetMapping("/add")
    @ResponseBody
    public Result add() {
        // 查询所有的角色
        return success(roleService.selectAll());
    }

    @RequiresPermissions("admin_user:add")
    @PostMapping("/add")
    @ResponseBody
    public Result save(AdminUser adminUser) {
        adminUser.setInTime(new Date());
        adminUser.setPassword(new BCryptPasswordEncoder().encode(adminUser.getPassword()));
        adminUserService.insert(adminUser);
        return success();
    }

    @RequiresPermissions("admin_user:edit")
    @GetMapping("/edit")
    @ResponseBody
    public Result edit(Integer id) {
        //    Assert.isTrue(getAdminUser().getId().equals(id), "谁给你的权限让你修改别人的帐号的？");
        Map<String, Object> data = new HashMap<>();
        // 查询所有的角色
        data.put("roles", roleService.selectAll());
        data.put("adminUser", adminUserService.selectById(id));
        return success(data);
    }

    @RequiresPermissions("admin_user:edit")
    @PostMapping("/edit")
    @ResponseBody
    public Result edit(AdminUser adminUser) {
        //    Assert.isTrue(getAdminUser().getId().equals(adminUser.getId()), "谁给你的权限让你修改别人的帐号的？");
        if (StringUtils.isEmpty(adminUser.getPassword())) {
            adminUser.setPassword(null);
        } else {
            adminUser.setPassword(new BCryptPasswordEncoder().encode(adminUser.getPassword()));
        }
        adminUserService.update(adminUser);
        return success();
    }

    @RequiresPermissions("admin_user:delete")
    @GetMapping("/delete")
    @ResponseBody
    public Result delete(Integer id) {
        adminUserService.delete(id);
        return success();
    }
}
