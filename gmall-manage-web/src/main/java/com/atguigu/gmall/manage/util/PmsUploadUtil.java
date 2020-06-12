package com.atguigu.gmall.manage.util;

import org.csource.fastdfs.ClientGlobal;
import org.csource.fastdfs.StorageClient;
import org.csource.fastdfs.TrackerClient;
import org.csource.fastdfs.TrackerServer;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public class PmsUploadUtil {


    public static String uploadImage(MultipartFile multipartFile) {

        StringBuilder file_url = new StringBuilder("http://192.168.51.213:8888");

        // 配置fdfs的全局链接地址
        String tracker = PmsUploadUtil.class.getResource("/tracker.conf").getPath();// 获得配置文件的路径
        try {
            ClientGlobal.init(tracker);
        } catch (Exception e) {
            e.printStackTrace();
        }

        TrackerClient trackerClient = new TrackerClient();

        // 获得一个trackerServer的实例
        TrackerServer trackerServer = null;
        try {
            trackerServer = trackerClient.getConnection();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 通过tracker获得一个Storage链接客户端
        StorageClient storageClient = new StorageClient(trackerServer, null); //第二个参数：是否指定一个storage

        try {
            byte[] bytes = multipartFile.getBytes(); // 获得上传的二进制对象
            // 获取文件后缀
            String originalFilename = multipartFile.getOriginalFilename();
            int i = originalFilename.lastIndexOf(".");
            String suffix = originalFilename.substring(i + 1);
            String[] uploadInfos = storageClient.upload_file(bytes, suffix, null);
            for (String uploadInfo : uploadInfos) {
                file_url.append("/").append(uploadInfo);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file_url.toString();
    }
}
