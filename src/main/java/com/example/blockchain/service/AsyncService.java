package com.example.blockchain.service;

import com.alibaba.fastjson.JSON;
import org.hyperledger.fabric.gateway.Contract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Service
public class AsyncService {

    @Autowired
    private Contract contract;

    private final BlockingQueue<Map<String, String>> evidenceQueue = new LinkedBlockingQueue<>();
    
    // 最大重试次数
    private static final int MAX_RETRY = 3;

    public void addToQueue(Map<String, String> payload) {
        if (!payload.containsKey("retryCount")) {
            payload.put("retryCount", "0");
        }
        evidenceQueue.offer(payload);
        System.out.println(">>> [队列] 加入缓存 (当前积压: " + evidenceQueue.size() + ")");
    }

    @PostConstruct
    public void startWorker() {
        new Thread(() -> {
            System.out.println(">>> [后台线程] 批量处理器已启动 (含熔断机制)...");
            while (true) {
                try {
                    List<Map<String, String>> batch = new ArrayList<>();
                    
                    // 1. 阻塞获取第1条
                    Map<String, String> first = evidenceQueue.poll(1, TimeUnit.SECONDS);
                    if (first == null) continue; 
                    
                    batch.add(first);
                    
                    // 2. 贪婪获取剩余 (最多凑100条)
                    evidenceQueue.drainTo(batch, 99);

                    // 3. 处理
                    processBatchTransaction(batch);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }

    private void processBatchTransaction(List<Map<String, String>> batch) {
        try {
            System.out.println(">>> [批量上链] 正在打包 " + batch.size() + " 条数据...");
            String batchJson = JSON.toJSONString(batch);
            byte[] result = contract.submitTransaction("submitEvidenceBatch", batchJson);
            System.out.println("✅ [成功] 批量 TxID: " + new String(result, StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("❌ [失败] 上链异常: " + e.getMessage());
            
            // --- 关键：熔断重试机制 ---
            // 如果批量失败（比如因为含有一个重复ID），我们把它们打散，重新塞回队列一个个试
            System.out.println("⚠️ [Fallback] 触发熔断重试机制，将打散重试...");
            
            for (Map<String, String> item : batch) {
                int retry = Integer.parseInt(item.getOrDefault("retryCount", "0"));
                if (retry < MAX_RETRY) {
                    item.put("retryCount", String.valueOf(retry + 1));
                    evidenceQueue.offer(item); // 重新入队
                    System.out.println("   -> ID: " + item.get("eventID") + " 已回炉重造 (重试第 " + (retry + 1) + " 次)");
                } else {
                    System.err.println("   -> 💀 ID: " + item.get("eventID") + " 彻底失败，已丢弃！");
                }
            }
            // 稍微降速，防止雪崩
            try { Thread.sleep(2000); } catch (InterruptedException ex) {}
        }
    }
}
