package org.wang.fileshare.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for file operations
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
public class FileController {

    private static final String UPLOAD_DIR = System.getProperty("java.io.tmpdir") + "/file-share/";

    /**
     * Upload a file
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        if (file.isEmpty()) {
            response.put("success", false);
            response.put("error", "File is empty");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // Create upload directory if not exists
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Save file
            Path filePath = uploadPath.resolve(file.getOriginalFilename());
            Files.copy(file.getInputStream(), filePath);
            
            response.put("success", true);
            response.put("filename", file.getOriginalFilename());
            response.put("size", file.getSize());
            response.put("message", "File uploaded successfully");
            
            log.info("File uploaded: {} ({} bytes)", file.getOriginalFilename(), file.getSize());
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            response.put("success", false);
            response.put("error", "Failed to upload file: " + e.getMessage());
            log.error("Upload failed", e);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * List all files
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listFiles() {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, String>> fileList = new ArrayList<>();

        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (Files.exists(uploadPath)) {
                Files.list(uploadPath).forEach(path -> {
                    File file = path.toFile();
                    if (file.isFile()) {
                        Map<String, String> fileInfo = new HashMap<>();
                        fileInfo.put("name", file.getName());
                        fileInfo.put("size", String.valueOf(file.length()));
                        fileInfo.put("lastModified", String.valueOf(file.lastModified()));
                        fileList.add(fileInfo);
                    }
                });
            }
            
            response.put("success", true);
            response.put("files", fileList);
            response.put("count", fileList.size());
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            response.put("success", false);
            response.put("error", "Failed to list files: " + e.getMessage());
            log.error("List files failed", e);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Delete a file
     */
    @DeleteMapping("/delete/{filename}")
    public ResponseEntity<Map<String, Object>> deleteFile(@PathVariable String filename) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Path filePath = Paths.get(UPLOAD_DIR).resolve(filename);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                response.put("success", true);
                response.put("message", "File deleted successfully");
                log.info("File deleted: {}", filename);
            } else {
                response.put("success", false);
                response.put("error", "File not found");
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            response.put("success", false);
            response.put("error", "Failed to delete file: " + e.getMessage());
            log.error("Delete failed", e);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Download a file
     */
    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(UPLOAD_DIR).resolve(filename);
            File file = filePath.toFile();
            
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new FileSystemResource(file);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(file.length())
                    .body(resource);
        } catch (Exception e) {
            log.error("Download failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get server info including local IP address for QR code
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getServerInfo() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("p2pPort", 8089);
        response.put("uploadDir", UPLOAD_DIR);
        response.put("localIp", getLocalIpAddress());
        response.put("port", 8088);
        return ResponseEntity.ok(response);
    }

    /**
     * Get local IP address for network access
     */
    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(interfaces)) {
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                for (InetAddress addr : Collections.list(addresses)) {
                    if (!addr.isLoopbackAddress() && !addr.isLinkLocalAddress() && !addr.isAnyLocalAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip != null && ip.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to get local IP", e);
        }
        return "localhost";
    }
}
