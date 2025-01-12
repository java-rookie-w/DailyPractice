package org.wang.mianshi.tools;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

public class ImageBase64Convert {
    public static void main(String[] args) {
        System.out.println("start convert");
        try {
            // 图像文件路径
            String imagePath = "C:\\Users\\wangrk\\OneDrive\\图片\\Camera Roll\\微信图片_20240105174914.jpg";

            // 读取图像文件到字节数组
            byte[] imageBytes = Files.readAllBytes(Paths.get(imagePath));

            // 对字节数组进行Base64编码
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // 输出Base64编码后的字符串
            System.out.println(base64Image);
            System.out.println("origin image size: " +imageBytes.length + ". Base64 length (imageSize / 3 * 4):" + base64Image.length());
        } catch (Exception e) {
            e.    printStackTrace();
        }
    }
}

