package com.example.blockchain;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.blockchain.service.AsyncService;
import org.hyperledger.fabric.gateway.Contract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class BlockchainController {

    @Autowired
    private Contract contract;

    @Autowired
    private AsyncService asyncService;

    

    // 1. 提交接口 (保持不变)
    @PostMapping("/evidence")
    public Map<String, Object> submitEvidence(@RequestBody Map<String, String> payload) {
        Map<String, Object> response = new HashMap<>();
        if (!payload.containsKey("eventID") || !payload.containsKey("dataHash")) {
            response.put("status", "error");
            response.put("message", "缺少必要参数");
            return response;
        }
        asyncService.addToQueue(payload);
        response.put("status", "success");
        response.put("message", "已加入后台队列");
        return response;
    }

    // 2. 查询接口 (保持不变)
    @GetMapping("/evidence/{id}")
    public Map<String, Object> getEvidence(@PathVariable String id) {
        Map<String, Object> response = new HashMap<>();
        try {
            System.out.println(">>> 正在查询: " + id);
            byte[] result = contract.evaluateTransaction("getEvidenceByEventID", id);
            String jsonStr = new String(result, StandardCharsets.UTF_8);
            response.put("status", "success");
            response.put("data", JSON.parseObject(jsonStr));
            return response;
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "未找到: " + e.getMessage());
            return response;
        }
    }
    
    // 3. 验证接口 (🔥🔥🔥 关键修复点)
    @PostMapping("/verify")
    public Map<String, Object> verifyEvidence(@RequestBody Map<String, String> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            String id = payload.get("eventID");
            String clientHash = payload.get("dataHash"); // 前端传来的“本地”哈希

            // 查链上数据
            byte[] result = contract.evaluateTransaction("getEvidenceByEventID", id);
            JSONObject chainData = JSON.parseObject(new String(result, StandardCharsets.UTF_8));
            
            // 拿到链上哈希
            String chainHash = chainData.getString("dataHash");

            boolean isMatch = chainHash.equals(clientHash);
            
            response.put("status", "success");
            response.put("isMatch", isMatch);
            
            // ✅ 补全了下面这两行，前端才能显示出来！
            response.put("chainHash", chainHash); 
            response.put("localHash", clientHash);
            
            return response;
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "校验失败: " + e.getMessage());
            return response;
        }
    }
}
