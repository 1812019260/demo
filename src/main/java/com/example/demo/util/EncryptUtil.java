package com.example.demo.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class EncryptUtil {
    /**
     * 加密并签名
     */
    public static String encryptAndSign(String data) {
        // 实际项目中应使用RSA和AES进行加密和签名
        // 这里简化处理,直接返回JSON
        Map<String, Object> result = new HashMap<>();
        result.put("data", data);
        result.put("signature", "mock_signature");
        result.put("encryptedKey", "mock_encrypted_key");
        result.put("encryptedData", "mock_encrypted_data");

        return JSON.toJSONString(result);
    }

    /**
     * 验证签名并解密
     */
    public static String verifyAndDecrypt(String encryptedData) {
        // 实际项目中应验证RSA签名并使用AES解密
        // 这里简化处理,直接返回数据
        JSONObject json = JSON.parseObject(encryptedData);
        return json.getString("data");
    }
}
