package com.github.cloudgyb.http.proxy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * SSL/TLS证书配置
 *
 * @author cloudgyb
 */
public class SSLCertConfig {
    private final File privateKey;
    private final File cert;

    public SSLCertConfig(File privateKey, File cert) {
        this.privateKey = privateKey;
        this.cert = cert;
    }

    public File getPrivateKey() {
        return this.privateKey;
    }

    public File getCert() {
        return this.cert;
    }

    public InputStream getPrivateKeyInputStream() throws FileNotFoundException {
        return new FileInputStream(this.privateKey);
    }

    public InputStream getCertInputStream() throws FileNotFoundException {
        return new FileInputStream(this.cert);
    }


    public void validate() {

    }
}
