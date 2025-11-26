package com.example.blockchain;

import com.alibaba.fastjson.JSON;
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

    // 1. 提交接口 (升级版：只入队，不等待)
    @PostMapping("/evidence")
    public Map<String, Object> submitEvidence(@RequestBody Map<String, String> payload) {
        Map<String, Object> response = new HashMap<>();
        
        if (!payload.containsKey("eventID") || !payload.containsKey("dataHash")) {
            response.put("status", "error");
            response.put("message", "缺少必要参数");
            return response;
        }

        // 核心修改：调用异步服务
        asyncService.addToQueue(payload);

        response.put("status", "success");
        response.put("message", "已加入后台队列");
        return response;
    }

    // 2. 查询接口
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
    
    // 3. 验证接口
    @PostMapping("/verify")
    public Map<String, Object> verifyEvidence(@RequestBody Map<String, String> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            String id = payload.get("eventID");
            String clientHash = payload.get("dataHash");
            byte[] result = contract.evaluateTransaction("getEvidenceByEventID", id);
            String chainHash = JSON.parseObject(new String(result, StandardCharsets.UTF_8)).getString("dataHash");
            response.put("status", "success");
            response.put("isMatch", chainHash.equals(clientHash));
            return response;
        } catch (Exception e) {
            response.put("status", "error");
            return response;
        }
    }
}
