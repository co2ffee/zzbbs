package co.yiiu.pybbs.controller.front;

import co.yiiu.pybbs.controller.api.BaseApiController;
import co.yiiu.pybbs.util.FileUtil;
import co.yiiu.pybbs.util.captcha.Captcha;
import co.yiiu.pybbs.util.captcha.GifCaptcha;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Created by tomoya.
 * Copyright (c) 2018, All Rights Reserved.
 * https://atjiu.github.io
 */
@Controller
@RequestMapping("/common")
public class CommonController extends BaseApiController {

    @Resource
    private FileUtil fileUtil;

    // gif 验证码
    @GetMapping("/captcha")
    public void captcha(HttpServletResponse response, HttpSession session) throws IOException {
        // 响应头要在写出图片内容之前设置：明确是 gif，并且不允许缓存（验证码每次都不一样）
        response.setContentType("image/gif");
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        Captcha captcha = new GifCaptcha();
        captcha.out(response.getOutputStream());
        String text = captcha.text();
        session.setAttribute("_captcha", text);
    }

    @GetMapping("/show_img")
    public String showOssImg(String name) {
        return "redirect:" + fileUtil.generatorOssUrl(name);
    }

}
