package org.wang.jvmlab.classloader;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 【考点 20 / 21】自定义类加载器：真正从文件系统加载一个类
 *
 * 【运行】
 *   java org.wang.jvmlab.classloader.CustomClassLoaderDemo
 *
 * 【预期现象】
 *   1. 先把 Plugin.class 复制到 external-classes/ 目录（模拟外部插件）
 *   2. 用自定义 FileClassLoader（parent = PlatformClassLoader）加载它
 *   3. 打印出：该类由 FileClassLoader 加载，且 **与 classpath 上的 Plugin 不是同一个类**
 *
 * 【面试要点】
 *   1. 自定义加载器只需重写 findClass()（遵守委派），不要动 loadClass()
 *      （那是委派逻辑本身，改了才叫"打破委派"）。
 *   2. 为什么这里 parent 设成 Platform 而不是默认 App？
 *      因为默认 parent 是 App，它能在 classpath 上找到 Plugin，就轮不到我们 defineClass 了。
 *      真实插件场景（Tomcat、SPI、热部署）也是靠控制 parent 实现隔离。
 *   3. 命名空间隔离：同一个类被两个加载器加载 → 是两个不同的 Class 对象，
 *      互相 instanceof / 强转都会失败。这是 ClassCastException 的经典疑难杂症来源。
 *   4. 类卸载条件很苛刻：Class 对象、加载器、所有实例三者都不可达才行 ——
 *      所以热部署频繁重载类容易撑爆元空间（见 MetaspaceOomDemo）。
 */
public class CustomClassLoaderDemo {

    public static void main(String[] args) throws Exception {
        // 1. 把 Plugin.class 复制到外部目录，模拟"外部插件"
        String classFileName = "CustomClassLoaderDemo$Plugin.class";
        String className = "org.wang.jvmlab.classloader.CustomClassLoaderDemo$Plugin";

        Path pluginDir = Paths.get("external-classes");
        Path copied = pluginDir.resolve("org/wang/jvmlab/classloader/" + classFileName);
        Files.createDirectories(copied.getParent());
        try (InputStream in = CustomClassLoaderDemo.class.getResourceAsStream(classFileName)) {
            if (in == null) {
                throw new IllegalStateException("找不到编译好的 " + classFileName + "，请先编译本模块");
            }
            Files.write(copied, in.readAllBytes());
        }
        System.out.println("已把 " + classFileName + " 复制到：" + copied.toAbsolutePath());

        // 2. 用自定义加载器加载（parent = Platform，强制它自己干活）
        FileClassLoader loader = new FileClassLoader(pluginDir.toString(),
                ClassLoader.getPlatformClassLoader());
        Class<?> fromFile = loader.loadClass(className);

        System.out.println("\n========== 加载结果 ==========");
        System.out.println("  由谁加载        ：" + fromFile.getClassLoader());
        System.out.println("  classpath 上的  ：" + Plugin.class.getClassLoader());
        System.out.println("  两者是同一个类吗：" + (fromFile == Plugin.class) + "  ← 命名空间隔离");

        System.out.println("\n========== 反射调用外部加载的类 ==========");
        Object instance = fromFile.getDeclaredConstructor().newInstance();
        fromFile.getMethod("run").invoke(instance);
        System.out.println("  注意：这里只能用反射调用，因为编译期类型是另一个命名空间里的类");
    }

    /** 从指定目录按全限定名读取 .class 文件的加载器 */
    static class FileClassLoader extends ClassLoader {

        private final String baseDir;

        FileClassLoader(String baseDir, ClassLoader parent) {
            super(parent);
            this.baseDir = baseDir;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String relative = name.replace('.', '/') + ".class";
            Path file = Paths.get(baseDir, relative);
            try {
                byte[] bytes = Files.readAllBytes(file);
                System.out.println("  [FileClassLoader] 自己读取并 defineClass：" + file);
                return defineClass(name, bytes, 0, bytes.length);
            } catch (Exception e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }

    /** 被加载的"插件"类 */
    public static class Plugin {
        public void run() {
            System.out.println("  插件 Plugin.run() 执行成功 —— 这段代码不在 classpath 加载路径上");
        }
    }
}
