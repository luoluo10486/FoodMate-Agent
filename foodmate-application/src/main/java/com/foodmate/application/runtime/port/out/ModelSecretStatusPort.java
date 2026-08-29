package com.foodmate.application.runtime.port.out;

/** 仅读取不可逆的供应商密钥状态，密钥材料不得跨越此端口。 */
public interface ModelSecretStatusPort {
    SecretStatus status(String providerCode);

    record SecretStatus(boolean configured, String fingerprint) {}
}
