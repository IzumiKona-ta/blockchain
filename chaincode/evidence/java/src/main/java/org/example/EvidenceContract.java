package org.example;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.ChaincodeStub;
// ✅✅✅ 这里！把 shim 改成了 contract
import org.hyperledger.fabric.contract.ClientIdentity; 
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Contract(name = "evidence")
@Default
public class EvidenceContract implements ContractInterface {

    // 允许写入的 MSP ID (Org1)
    private static final String ALLOWED_WRITER_MSP = "Org1MSP";

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void initLedger(final Context ctx) {}

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String submitEvidence(final Context ctx, String eventID, String dataHash, String metadata) {
        checkWriterPermission(ctx); // 🔒 权限校验
        saveEvidence(ctx, eventID, dataHash, metadata);
        return "SUCCESS: " + eventID;
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String submitEvidenceBatch(final Context ctx, String batchJson) {
        checkWriterPermission(ctx); // 🔒 权限校验
        
        JSONArray items = JSON.parseArray(batchJson);
        List<String> successIds = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            saveEvidence(ctx, item.getString("eventID"), item.getString("dataHash"), item.getString("metadata"));
            successIds.add(item.getString("eventID"));
        }
        return "BATCH SUCCESS: " + successIds.toString();
    }

    // 内部权限检查方法
    private void checkWriterPermission(Context ctx) {
        // ✅ 因为上面 import 改对了，这里就能找到类了
        ClientIdentity client = ctx.getClientIdentity();
        String mspId = client.getMSPID();
        
        if (!ALLOWED_WRITER_MSP.equals(mspId)) {
            throw new RuntimeException("🚫 权限不足！当前用户属于 " + mspId + "，只有 " + ALLOWED_WRITER_MSP + " 有权上链。");
        }
    }

    private void saveEvidence(Context ctx, String eventID, String dataHash, String metadata) {
        ChaincodeStub stub = ctx.getStub();
        String evidenceState = stub.getStringState(eventID);
        if (evidenceState != null && !evidenceState.isEmpty()) {
            throw new RuntimeException("Evidence " + eventID + " already exists");
        }
        
        String submitter = ctx.getClientIdentity().getMSPID();
        Evidence evidence = new Evidence(eventID, dataHash, metadata, Instant.now().getEpochSecond(), submitter);
        stub.putStringState(eventID, JSON.toJSONString(evidence));
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String getEvidenceByEventID(final Context ctx, String eventID) {
        ChaincodeStub stub = ctx.getStub();
        String evidenceJSON = stub.getStringState(eventID);
        if (evidenceJSON == null || evidenceJSON.isEmpty()) {
            throw new RuntimeException("Evidence " + eventID + " does not exist");
        }
        return evidenceJSON;
    }
}
