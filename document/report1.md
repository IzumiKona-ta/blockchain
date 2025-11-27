# 区块链安全存证系统实施与学习报告

**技术栈：** Hyperledger Fabric v2.5, Java (Chaincode), Spring Boot (Backend), Docker, WSL

---

## 1. 项目背景与目标

### 1.1 项目背景
传统安全告警日志存储在中心化数据库中，存在被内部人员篡改或黑客删除的风险，导致审计困难。

### 1.2 项目目标
构建一个基于 **联盟链 (Consortium Blockchain)** 的安全存证系统，实现以下核心能力：
1.  **不可篡改**：所有告警数据上链存储，确保数据唯一性和完整性。
2.  **可追溯**：提供全链路的交易哈希 (TxID) 追踪。
3.  **高性能**：支持高并发场景下的告警接入。
4.  **权限管控**：仅授权组织（如安全中心）可写入，其他组织（如监管）只读。

---

## 2. 实施路线与里程碑 (Day 1 - Day 7)

本项目严格按照《区块链板块实施细则》推进，并在技术深度上进行了扩展。

### ✅ 第一阶段：基础设施搭建 (Day 1)
* **环境构建**：在 Windows + WSL2 (Ubuntu) 环境下，成功部署 Docker 和 Fabric 运行环境。
* **网络组网**：搭建了 **2 Org, 2 Peer, 1 Orderer** 的标准联盟链网络架构。
* **通道管理**：成功创建并加入了 `mychannel` 通道。

### ✅ 第二阶段：智能合约开发 (Day 2 & 6)
* **技术选型**：由 Go 语言切换为 **Java**，利用 Java 生态的强类型优势。
* **合约逻辑**：
    * 定义了 `Evidence` (存证) 数据模型。
    * 实现了 `submitEvidence` (上链) 和 `getEvidence` (查询) 接口。
* **权限控制 (RBAC)**：
    * 集成 `ClientIdentity` 组件。
    * 实现了 **组织级权限校验**：拦截非 `Org1MSP` 成员的写入请求，确保数据安全。

### ✅ 第三阶段：后端服务封装 (Day 3 & 4)
* **中间件开发**：构建 **Spring Boot** 后端应用。
* **SDK 集成**：使用 `fabric-gateway-java` 实现后端与区块链节点的连接。
* **全栈闭环**：开发了可视化 Web 界面，实现了从“网页输入 -> 后端处理 -> 区块链存储 -> 网页验证”的完整业务闭环。

### ✅ 第四阶段：性能优化与高可用 (Day 5)
* **异步削峰**：引入内存消息队列 (`BlockingQueue`)，解耦前端请求与区块链交易。
* **批量上链 (Batching)**：
    * 开发 `submitEvidenceBatch` 合约接口。
    * 实现后端聚合逻辑（自动打包或满 100 条打包），**TPS 理论提升 10-100 倍**。
* **容错机制 (Fallback)**：实现了熔断重试逻辑，当批量上链因数据冲突失败时，自动打散重试，保证数据不丢失。

---

## 3. 核心技术难点与解决方案

在项目实施过程中，攻克了多个深层次的技术难题：

### 3.1 依赖与环境兼容性问题
* **问题**：Java Chaincode 在 Docker 容器内编译失败，Maven 依赖下载超时或找不到包 (`ClientIdentity` 符号缺失)。
* **解决**：
    * 配置 **阿里云镜像** 加速基础包下载。
    * 引入 **JitPack** 仓库解决第三方依赖 (`everit-json-schema`) 缺失。
    * 修正代码包路径引用 (`org.hyperledger.fabric.contract` vs `shim`)。
    * 采用 **本地编译 (Local Build)** 策略，绕过容器环境限制。

### 3.2 网络连接与 DNS 问题
* **问题**：WSL 重启后 IP 变动，导致后端报错 `UnknownHostException`，无法连接 Peer 节点。
* **解决**：
    * 配置 `/etc/hosts` 建立本地 DNS 映射。
    * 修改 `/etc/wsl.conf` 固化网络配置，防止重启失效。

### 3.3 链码生命周期管理
* **问题**：升级合约时报错 `sequence number mismatch`，导致部署失败。
* **解决**：深入理解 Fabric Lifecycle（生命周期），手动同步 Sequence 序号，成功完成了从 v1.0 到 v1.3 的多次平滑升级。

---

## 4. 系统架构图

```mermaid
graph TD
    User[用户/安全设备] -->|HTTP POST| Web[Web 前端]
    Web -->|Rest API| Backend[Spring Boot 后端]
    
    subgraph "Backend Service (后端核心)"
        Controller[控制器] --> Async[异步队列 Service]
        Async -->|打包 Batch| Batcher[批量处理器]
        Batcher -->|SDK Submit| Gateway[Fabric Gateway]
    end
    
    subgraph "Blockchain Network (Fabric 联盟链)"
        Gateway --> Peer1[Peer0.Org1 (背书)]
        Gateway --> Peer2[Peer0.Org2 (背书)]
        Peer1 & Peer2 -->|Proposal Response| Orderer[Orderer 排序节点]
        Orderer -->|Block| Ledger[分布式账本]
    end
    
    subgraph "Smart Contract (Java 链码)"
        Ledger -->|1. 权限检查| RBAC[权限控制]
        RBAC -->|2. 数据去重| Dedup[防重复检查]
        Dedup -->|3. 写入状态| StateDB[CouchDB / LevelDB]
    end