package co.yiiu.pybbs.controller.admin;

import co.yiiu.pybbs.model.Permission;
import co.yiiu.pybbs.service.IPermissionService;
import co.yiiu.pybbs.util.Result;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by tomoya.
 * Copyright (c) 2018, All Rights Reserved.
 * https://atjiu.github.io
 */
@Controller
@RequestMapping("/admin/permission")
public class PermissionAdminController extends BaseAdminController {

    @Resource
    private IPermissionService permissionService;

    @RequiresPermissions("permission:list")
    @GetMapping("/list")
    @ResponseBody
    public Result list(Integer pid) {
        List<Permission> permissions = permissionService.selectByPid(0);
        List<Permission> childPermissions;
        if (pid == null || pid == 0) {
            childPermissions = permissionService.selectByPid(permissions.get(0).getId());
        } else {
            childPermissions = permissionService.selectByPid(pid);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("permissions", permissions);
        data.put("childPermissions", childPermissions);
        data.put("pid", pid);
        return success(data);
    }

    @RequiresPermissions("permission:add")
    @PostMapping("/add")
    @ResponseBody
    public Result add(Permission permission) {
        permission = permissionService.insert(permission);
        return success(permission.getPid() == 0 ? permission.getId() : permission.getPid());
    }

    @RequiresPermissions("permission:edit")
    @PostMapping("/edit")
    @ResponseBody
    public Result edit(Permission permission) {
        permission = permissionService.update(permission);
        return success(permission.getPid() == 0 ? permission.getId() : permission.getPid());
    }

    @RequiresPermissions("permission:delete")
    @GetMapping("/delete")
    @ResponseBody
    public Result delete(Integer id) {
        permissionService.delete(id);
        return success();
    }
}
