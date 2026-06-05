package org.wang.fileshare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * P2P File Sharing Application
 * Simple and fast file transfer between devices
 */
@SpringBootApplication
public class FileShareApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileShareApplication.class, args);
        System.out.println("\n===========================================");
        System.out.println("  P2P File Share Started Successfully!");
        System.out.println("  Web: http://localhost:8088");
        System.out.println("  P2P: port 8089");
        System.out.println("===========================================\n");
    }
}
