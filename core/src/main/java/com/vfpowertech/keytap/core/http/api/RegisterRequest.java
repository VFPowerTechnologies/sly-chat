package com.vfpowertech.keytap.core.http.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class RegisterRequest {
    @JsonProperty("username")
    @NotNull
    final String username;

    @JsonProperty("metadata")
    @NotNull
    final Map<String, String> metadata;

    @JsonProperty("hash")
    @NotNull
    final String hash;

    @JsonProperty("hash-params")
    @NotNull
    final String hashParams;

    @JsonProperty("pubkey")
    @NotNull
    final String publicKey;

    @JsonProperty("prikey-enc")
    @NotNull
    final String encryptedPrivateKey;

    @JsonProperty("key-hash-params")
    @NotNull
    final String keyHashParams;

    @JsonProperty("key-enc-params")
    @NotNull
    final String keyEncryptionParams;

    public RegisterRequest(@NotNull String username, @NotNull Map<String, String> metadata, @NotNull String hash, @NotNull String hashParams, @NotNull String publicKey, @NotNull String encryptedPrivateKey, @NotNull String keyHashParams, @NotNull String keyEncryptionParams) {
        this.username = username;
        this.metadata = metadata;
        this.hash = hash;
        this.hashParams = hashParams;
        this.publicKey = publicKey;
        this.encryptedPrivateKey = encryptedPrivateKey;
        this.keyHashParams = keyHashParams;
        this.keyEncryptionParams = keyEncryptionParams;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RegisterRequest that = (RegisterRequest) o;

        if (!username.equals(that.username)) return false;
        if (!metadata.equals(that.metadata)) return false;
        if (!hash.equals(that.hash)) return false;
        if (!hashParams.equals(that.hashParams)) return false;
        if (!publicKey.equals(that.publicKey)) return false;
        if (!encryptedPrivateKey.equals(that.encryptedPrivateKey)) return false;
        if (!keyHashParams.equals(that.keyHashParams)) return false;
        return keyEncryptionParams.equals(that.keyEncryptionParams);

    }

    @Override
    public int hashCode() {
        int result = username.hashCode();
        result = 31 * result + metadata.hashCode();
        result = 31 * result + hash.hashCode();
        result = 31 * result + hashParams.hashCode();
        result = 31 * result + publicKey.hashCode();
        result = 31 * result + encryptedPrivateKey.hashCode();
        result = 31 * result + keyHashParams.hashCode();
        result = 31 * result + keyEncryptionParams.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "RegisterRequest{" +
                "username='" + username + '\'' +
                ", metadata=" + metadata +
                ", hash='" + hash + '\'' +
                ", hashParams='" + hashParams + '\'' +
                ", publicKey='" + publicKey + '\'' +
                ", encryptedPrivateKey='" + encryptedPrivateKey + '\'' +
                ", keyHashParams='" + keyHashParams + '\'' +
                ", keyEncryptionParams='" + keyEncryptionParams + '\'' +
                '}';
    }
}
