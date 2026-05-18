package com.example.tclremote;

import android.content.Context;
import android.content.SharedPreferences;
import android.sun.security.x509.*;
import java.io.ByteArrayInputStream;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Random;

public class CertificateStore {
    public static StoredIdentity loadOrCreate(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences("remote_cert_store", Context.MODE_PRIVATE);
        String keyText = prefs.getString("private_key", null);
        String certText = prefs.getString("certificate", null);
        if (keyText != null && certText != null) {
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(keyText)));
            Certificate cert = CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(certText)));
            return new StoredIdentity(key, cert);
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        Certificate cert = createCertificate(pair);
        prefs.edit()
                .putString("private_key", Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()))
                .putString("certificate", Base64.getEncoder().encodeToString(cert.getEncoded()))
                .apply();
        return new StoredIdentity(pair.getPrivate(), cert);
    }

    private static Certificate createCertificate(KeyPair pair) throws Exception {
        String algorithm = "SHA512withRSA";
        Date from = new Date();
        Date to = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);
        X500Name owner = new X500Name("CN=TCL Remote");
        CertificateExtensions ext = new CertificateExtensions();
        ext.set("SubjectKeyIdentifier", new SubjectKeyIdentifierExtension(new KeyIdentifier(pair.getPublic()).getIdentifier()));
        ext.set("PrivateKeyUsage", new PrivateKeyUsageExtension(from, to));
        X509CertInfo info = new X509CertInfo();
        info.set("version", new CertificateVersion(2));
        info.set("serialNumber", new CertificateSerialNumber(new Random().nextInt() & Integer.MAX_VALUE));
        info.set("algorithmID", new CertificateAlgorithmId(AlgorithmId.get(algorithm)));
        info.set("subject", new CertificateSubjectName(owner));
        info.set("key", new CertificateX509Key(pair.getPublic()));
        info.set("validity", new CertificateValidity(from, to));
        info.set("issuer", new CertificateIssuerName(owner));
        info.set("extensions", ext);
        X509CertImpl cert = new X509CertImpl(info);
        cert.sign(pair.getPrivate(), algorithm);
        return cert;
    }

    public static class StoredIdentity {
        public final PrivateKey privateKey;
        public final Certificate certificate;
        public StoredIdentity(PrivateKey privateKey, Certificate certificate) {
            this.privateKey = privateKey;
            this.certificate = certificate;
        }
    }
}
