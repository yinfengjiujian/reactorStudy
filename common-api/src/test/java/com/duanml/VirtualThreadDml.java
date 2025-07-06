package com.duanml;

/**
 * <p>Title: com.duanml</p>
 * <p>Company:爱尔信息中心</p>
 * <p>Copyright:Copyright(c)</p>
 * User: duanml
 * Date: 2025/7/6 09:50
 * Description: No Description
 */
public class VirtualThreadDml {

    public static void main(String[] args) {
        // 创建虚拟线程
        Thread virtualThread = Thread.startVirtualThread(() -> {
            System.out.println("Hello from a virtual thread!");
            try {
                Thread.sleep(5000); // 模拟一些工作
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Virtual thread work done.");
        });

        // 等待虚拟线程完成
        try {
            virtualThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("Main thread finished.");
    }
}
