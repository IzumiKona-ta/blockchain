import requests

# 批量测试（推荐）- 发送100条告警
response = requests.post(
    "http://127.0.0.1:5001/test/batch",
    json={"count": 100, "known_ratio": 0.6}
)
result = response.json()
print(f"成功: {result['results']['success']} 条")

# 发送单条告警
response = requests.post(
    "http://127.0.0.1:5001/test/send",
    json={"attack_type": "DDoS", "severity": 5, "confidence": 0.95}
)
print(response.json())

# 查看告警列表
response = requests.get("http://127.0.0.1:5000/alerts")
print(f"总告警数: {response.json()['total']}")