package co.yiiu.pybbs.util;

import co.yiiu.pybbs.service.ISystemConfigService;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.aliyun.oss.OSSClient;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.backblaze.b2.client.B2StorageClient;
import com.backblaze.b2.client.B2StorageClientFactory;
import com.backblaze.b2.client.contentSources.B2ByteArrayContentSource;
import com.backblaze.b2.client.contentSources.B2ContentSource;
import com.backblaze.b2.client.contentSources.B2ContentTypes;
import com.backblaze.b2.client.exceptions.B2Exception;
import com.backblaze.b2.client.structures.B2FileVersion;
import com.backblaze.b2.client.structures.B2UploadFileRequest;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;

/**
 * Created by tomoya.
 * Copyright (c) 2018, All Rights Reserved.
 * https://atjiu.github.io
 */
@Component
public class FileUtil {

    private final Logger log = LoggerFactory.getLogger(FileUtil.class);

    @Autowired
    private ISystemConfigService systemConfigService;

    @Autowired
    private ExecutorService executorService;


    public static String getFileMD5(InputStream in) {
        byte[] buffer = new byte[1024];
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
            while (true) {
                int len;
                if ((len = in.read(buffer, 0, 1024)) == -1) {
                    in.close();
                    break;
                }

                digest.update(buffer, 0, len);
            }
        } catch (Exception var6) {
            var6.printStackTrace();
            return null;
        }

        BigInteger bigInt = new BigInteger(1, digest.digest());
        return bigInt.toString(16);
    }

    /**
     * 上传文件
     *
     * @param file       要上传的文件对象
     * @param fileName   文件名，可以为空，为空的话，就生成一串uuid代替文件名
     * @param customPath 自定义存放路径，这个地址是跟在数据库里配置的路径后面的，
     *                   格式类似 avatar/admin 前后没有 / 前面表示头像，后面是用户的昵称，举例，如果将用户头像全都放在一个文件夹里，这里可以直接传个 avatar
     * @return
     */
    public String upload(MultipartFile file, String fileName, String customPath) {
        log.info("upload");
        try {
            if (file == null || file.isEmpty()) return null;

            if (StringUtils.isEmpty(fileName)) fileName = StringUtil.uuid();
            String suffix = "." + Objects.requireNonNull(file.getContentType()).split("/")[1];
            // 如果存放目录不存在，则创建
            File savePath = new File(systemConfigService.selectAllConfig().get("upload_path").toString() + customPath);
            if (!savePath.exists()) savePath.mkdirs();

            // 给上传的路径拼上文件名与后缀
            String localPath = systemConfigService.selectAllConfig().get("upload_path").toString() + customPath + "/" + fileName + suffix;
            File file1 = new File(localPath);
            if (file1.exists()) {
                file1.delete();
            }

            file.transferTo(file1);

            // 上传成功后返回访问路径
            return systemConfigService.selectAllConfig().get("static_url") + customPath + "/" + fileName +
                    suffix + "?v=" + StringUtil.randomNumber(6);
        } catch (IOException e) {
            log.error(e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * @param file       上传的文件
     * @param fileName   文件名，带后缀
     * @param customPath 上传到oss里的目录地址，如果只传到bucket下，请传空字符串""
     *                   例: 现在想将file放在 2021-03-08/test/avatar 目录里，那么这里的customPath就传 2021-03-08/test/avatar
     * @return
     */
    public String ossUpload(MultipartFile file, String fileName, String customPath) {
        try {
            String baseUrl = systemConfigService.selectAllConfig().get("base_url");
            String ossKey = systemConfigService.selectAllConfig().get("oss_key");
            String ossSecret = systemConfigService.selectAllConfig().get("oss_secret");
            String ossBucket = systemConfigService.selectAllConfig().get("oss_bucket");
            String ossEndPoint = systemConfigService.selectAllConfig().get("oss_end_point");
            if (StringUtils.isEmpty(fileName)) fileName = StringUtil.uuid();
            String suffix = "." + Objects.requireNonNull(file.getContentType()).split("/")[1];
            fileName += suffix;
            OSSClient ossClient = new OSSClient(ossEndPoint, ossKey, ossSecret);
            ossClient.putObject(ossBucket, customPath + "/" + fileName, file.getInputStream());
            ossClient.shutdown();
            return baseUrl + "/common/show_img?name=" + customPath + "/" + fileName;
        } catch (IOException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public String generatorOssUrl(String name) {
        String ossKey = systemConfigService.selectAllConfig().get("oss_key");
        String ossSecret = systemConfigService.selectAllConfig().get("oss_secret");
        String ossBucket = systemConfigService.selectAllConfig().get("oss_bucket");
        String ossEndPoint = systemConfigService.selectAllConfig().get("oss_end_point");
        Date expires = new Date(new Date().getTime() + 1000 * 60 * 60); // 1小时过期
        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(ossBucket, name);
        generatePresignedUrlRequest.setExpiration(expires);
        OSSClient client = new OSSClient(ossEndPoint, ossKey, ossSecret);
        URL url = client.generatePresignedUrl(generatePresignedUrlRequest);
        return url.toString();
    }

    public String qiniuUpload(MultipartFile file, String fileName, String customPath) {
        try {
            String qiniuKey = systemConfigService.selectAllConfig().get("qiniu_key");
            String qiniuSecret = systemConfigService.selectAllConfig().get("qiniu_secret");
            String qiniuBucket = systemConfigService.selectAllConfig().get("qiniu_bucket");
            String qiniuDomain = systemConfigService.selectAllConfig().get("qiniu_domain");
            if (StringUtils.isEmpty(fileName)) fileName = StringUtil.uuid();
            String suffix = "." + Objects.requireNonNull(file.getContentType()).split("/")[1];
            fileName += suffix;
            Configuration cfg = new Configuration(Region.autoRegion());
            UploadManager uploadManager = new UploadManager(cfg);
            Auth auth = Auth.create(qiniuKey, qiniuSecret);
            //默认不指定key的情况下，以文件内容的hash值作为文件名
            String key = fileName;
            String upToken = auth.uploadToken(qiniuBucket, key);
            log.info("qiniuKey:{},qiniuSecret:{},qiniuBucket:{},qiniuDomain:{}", qiniuKey, qiniuSecret, qiniuBucket, qiniuDomain);
            log.info("key:{},upToken:{}", key, upToken);
            Response response = uploadManager.put(file.getInputStream(), key, upToken, null, null);
            log.info("url:{}", qiniuDomain + "/" + fileName);
            //response.bodyString(): {"hash":"FrvhXY3VZrmU6_vUYLdQtk1KKlUH","key":"FrvhXY3VZrmU6_vUYLdQtk1KKlUH"}
            return qiniuDomain + "/" + fileName;
        } catch (IOException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public String backBlazeUpload(MultipartFile file, String fileName, String customPath) {
        try {
            String backBlazeKeyId = systemConfigService.selectAllConfig().get("backblaze_keyid");
            String backBlazeKey = systemConfigService.selectAllConfig().get("backblaze_key");
            String backBlazeBucket = systemConfigService.selectAllConfig().get("backblaze_bucket");
            String backBlazeDomain = systemConfigService.selectAllConfig().get("backblaze_domain");
            if (StringUtils.isEmpty(fileName)) fileName = StringUtil.uuid();
            String suffix = "." + Objects.requireNonNull(file.getContentType()).split("/")[1];
            fileName += suffix;
            //异步操作
            B2StorageClient uploadManager = B2StorageClientFactory
                    .createDefaultFactory()
                    .create(backBlazeKeyId, backBlazeKey, "b2_4j/0.0.1");
            //默认不指定key的情况下，以文件内容的hash值作为文件名
            String key = fileName;
            log.info("backBlazeKeyId:{},backBlazeKey:{},backBlazeBucket:{},backBlazeDomain:{}", backBlazeKeyId, backBlazeKey, backBlazeBucket, backBlazeDomain);
            B2ContentSource source = B2ByteArrayContentSource.build(file.getBytes());
            B2UploadFileRequest request = B2UploadFileRequest
                    .builder(backBlazeBucket, fileName, B2ContentTypes.B2_AUTO, source)
                    .setCustomField("color", "blue")
                    .build();
//            B2FileVersion b2FileVersion = uploadManager.uploadSmallFile(request);
            B2FileVersion b2FileVersion = uploadManager.uploadLargeFile(request, executorService);
            log.info("url:{}", backBlazeDomain + "/" + fileName);
            //response.bodyString(): {"hash":"FrvhXY3VZrmU6_vUYLdQtk1KKlUH","key":"FrvhXY3VZrmU6_vUYLdQtk1KKlUH"}
            return backBlazeDomain + "/" + fileName;
        } catch (IOException | B2Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }


    public static void main(String[] args) {
        try {
            String qiniuKey = "JG0fmIMS7wS8wMMXh6wAllcPSYYBUa8y2YoKICUI";
            String qiniuSecret = "RhNP3k8TbitmJ7J1rZ-tO0NSAXjNr4YtjmbYiLJ8";
            String qiniuBucket = "vedio-dlj";
            String qiniuDomain = "/aaa";
            byte[] uploadBytes = "hello qiniu cloud".getBytes("utf-8");
            ByteArrayInputStream byteInputStream = new ByteArrayInputStream(uploadBytes);
            String fileName = "abc";
            if (StringUtils.isEmpty(fileName)) fileName = StringUtil.uuid();
//            String suffix = "." + Objects.requireNonNull(file.getContentType()).split("/")[1];
//            fileName += suffix;
            Configuration cfg = new Configuration(Region.autoRegion());
            UploadManager uploadManager = new UploadManager(cfg);
            Auth auth = Auth.create(qiniuKey, qiniuSecret);
            //默认不指定key的情况下，以文件内容的hash值作为文件名
            String key = fileName;
            String upToken = auth.uploadToken(qiniuBucket, key);
            Response response = uploadManager.put(byteInputStream, key, upToken, null, null);
            System.out.println(response);
            //response.bodyString(): {"hash":"FrvhXY3VZrmU6_vUYLdQtk1KKlUH","key":"FrvhXY3VZrmU6_vUYLdQtk1KKlUH"}
            System.out.printf("qiniuDomain + \"/\" + fileName");
        } catch (IOException e) {

        }
    }
}
