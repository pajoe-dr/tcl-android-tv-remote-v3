package com.example.tclremote;

import com.example.tclremote.proto.PairingProto;
import com.example.tclremote.proto.RemoteProto;
import com.google.protobuf.ByteString;
import java.io.*;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import javax.net.ssl.*;

public class RemoteServiceClient {
    private final CertificateStore.StoredIdentity identity;
    private SSLSocket pairingSocket;
    private InputStream pairingIn;
    private OutputStream pairingOut;
    private SSLSocket remoteSocket;
    private OutputStream remoteOut;
    private InputStream remoteIn;
    private Thread keepaliveThread;
    private static final long KEEPALIVE_INTERVAL = 30000; // 30 seconds

    public RemoteServiceClient(CertificateStore.StoredIdentity identity) { this.identity = identity; }

    public void startPairing(String host) throws Exception {
        pairingSocket = socket(host, 6467);
        pairingIn = pairingSocket.getInputStream();
        pairingOut = pairingSocket.getOutputStream();
        writePairing(PairingProto.OuterMessage.newBuilder().setProtocolVersion(2).setStatus(PairingProto.OuterMessage.Status.STATUS_OK)
                .setPairingRequest(PairingProto.PairingRequest.newBuilder().setServiceName("atvremote").setClientName("TCL Remote")).build());
        readPairing();
        PairingProto.Options.Encoding encoding = PairingProto.Options.Encoding.newBuilder()
                .setType(PairingProto.Options.Encoding.EncodingType.ENCODING_TYPE_HEXADECIMAL)
                .setSymbolLength(6)
                .build();
        writePairing(PairingProto.OuterMessage.newBuilder().setProtocolVersion(2).setStatus(PairingProto.OuterMessage.Status.STATUS_OK)
                .setOptions(PairingProto.Options.newBuilder()
                        .setPreferredRole(PairingProto.Options.RoleType.ROLE_TYPE_INPUT)
                        .addInputEncodings(encoding)).build());
        readPairing();
        writePairing(PairingProto.OuterMessage.newBuilder().setProtocolVersion(2).setStatus(PairingProto.OuterMessage.Status.STATUS_OK)
                .setConfiguration(PairingProto.Configuration.newBuilder()
                        .setEncoding(encoding)
                        .setClientRole(PairingProto.Options.RoleType.ROLE_TYPE_INPUT)).build());
        readPairing();
    }

    public void finishPairing(String code) throws Exception {
        X509Certificate clientCert = (X509Certificate) identity.certificate;
        X509Certificate serverCert = (X509Certificate) pairingSocket.getSession().getPeerCertificates()[0];
        byte[] secret = secret(code, (RSAPublicKey) clientCert.getPublicKey(), (RSAPublicKey) serverCert.getPublicKey());
        if ((secret[0] & 0xFF) != Integer.parseInt(code.substring(0, 2), 16)) {
            throw new IllegalStateException("Pairing code did not validate");
        }
        writePairing(PairingProto.OuterMessage.newBuilder().setProtocolVersion(2).setStatus(PairingProto.OuterMessage.Status.STATUS_OK)
                .setSecret(PairingProto.Secret.newBuilder().setSecret(ByteString.copyFrom(secret))).build());
        PairingProto.OuterMessage response = readPairing();
        if (!response.hasSecretAck()) throw new IllegalStateException("Pairing rejected");
        pairingSocket.close();
    }

    public void connect(String host) throws Exception {
        remoteSocket = socket(host, 6466);
        remoteOut = remoteSocket.getOutputStream();
        remoteIn = remoteSocket.getInputStream();
        writeRemote(RemoteProto.RemoteMessage.newBuilder()
                .setRemoteConfigure(RemoteProto.RemoteConfigure.newBuilder().setCode1(622)
                        .setDeviceInfo(RemoteProto.RemoteDeviceInfo.newBuilder()
                                .setModel("TCL Remote")
                                .setVendor("OpenAI")
                                .setUnknown1(1)
                                .setUnknown2("1")
                                .setPackageName("com.example.tclremote")
                                .setAppVersion("1.0"))).build());
        writeRemote(RemoteProto.RemoteMessage.newBuilder()
                .setRemoteSetActive(RemoteProto.RemoteSetActive.newBuilder().setActive(622)).build());
        
        // Start keepalive thread to maintain connection
        startKeepalive();
    }

    public void sendKey(int keyCode) throws Exception {
        writeRemote(RemoteProto.RemoteMessage.newBuilder()
                .setRemoteKeyInject(RemoteProto.RemoteKeyInject.newBuilder().setKeyCode(keyCode).setDirection(1)).build());
        writeRemote(RemoteProto.RemoteMessage.newBuilder()
                .setRemoteKeyInject(RemoteProto.RemoteKeyInject.newBuilder().setKeyCode(keyCode).setDirection(2)).build());
    }

    /**
     * Send text input to the TV using character-by-character key codes
     */
    public void sendText(String text) throws Exception {
        if (text == null || text.isEmpty()) {
            return;
        }

        // Send character by character
        for (char c : text.toCharArray()) {
            sendChar(c);
            Thread.sleep(100); // Delay between characters to ensure processing
        }
    }

    /**
     * Send a single character using its key code
     */
    private void sendChar(char c) throws Exception {
        int keyCode = charToKeyCode(c);
        if (keyCode > 0) {
            sendKey(keyCode);
        }
    }

    /**
     * Convert a character to its Android KeyCode
     * Supports letters, numbers, and common special characters
     */
    private int charToKeyCode(char c) {
        // Numbers: KEYCODE_0 (7) through KEYCODE_9 (16)
        if (c >= '0' && c <= '9') {
            return 7 + (c - '0');
        }
        
        // Lowercase letters: KEYCODE_A (29) through KEYCODE_Z (54)
        if (c >= 'a' && c <= 'z') {
            return 29 + (c - 'a');
        }
        
        // Uppercase letters (same keycodes as lowercase)
        if (c >= 'A' && c <= 'Z') {
            return 29 + (c - 'A');
        }
        
        // Special characters
        switch (c) {
            case ' ':    return 62;  // KEYCODE_SPACE
            case '.':    return 56;  // KEYCODE_PERIOD
            case ',':    return 55;  // KEYCODE_COMMA
            case '\'':   return 73;  // KEYCODE_APOSTROPHE
            case '-':    return 69;  // KEYCODE_MINUS
            case '/':    return 76;  // KEYCODE_SLASH
            case '@':    return 77;  // KEYCODE_AT
            case '*':    return 17;  // KEYCODE_STAR (on keypad)
            case '#':    return 18;  // KEYCODE_POUND (on keypad)
            case '+':    return 81;  // KEYCODE_PLUS
            case ':':    return 74;  // KEYCODE_SEMICOLON
            case ';':    return 74;  // KEYCODE_SEMICOLON
            case '=':    return 70;  // KEYCODE_EQUALS
            case '[':    return 71;  // KEYCODE_LEFT_BRACKET
            case ']':    return 72;  // KEYCODE_RIGHT_BRACKET
            case '\n':   return 66;  // KEYCODE_ENTER
            case '\t':   return 61;  // KEYCODE_TAB
            default:     return 0;   // Unsupported character
        }
    }

    /**
     * Start a background thread to send keepalive messages
     * This prevents the connection from timing out after a few minutes
     */
    private void startKeepalive() {
        if (keepaliveThread != null && keepaliveThread.isAlive()) {
            return;
        }
        keepaliveThread = new Thread(() -> {
            while (remoteSocket != null && remoteSocket.isConnected()) {
                try {
                    Thread.sleep(KEEPALIVE_INTERVAL);
                    // Send a keepalive message to maintain the connection
                    writeRemote(RemoteProto.RemoteMessage.newBuilder()
                            .setRemoteSetActive(RemoteProto.RemoteSetActive.newBuilder().setActive(622)).build());
                } catch (Exception e) {
                    // Connection lost, exit keepalive
                    break;
                }
            }
        });
        keepaliveThread.setDaemon(true);
        keepaliveThread.start();
    }

    private SSLSocket socket(String host, int port) throws Exception {
        SSLContext ctx = SSLContext.getInstance("TLS");
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null);
        ks.setKeyEntry("remote", identity.privateKey, new char[0], new java.security.cert.Certificate[]{identity.certificate});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, new char[0]);
        ctx.init(kmf.getKeyManagers(), new TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }}, null);
        SSLSocket s = (SSLSocket) ctx.getSocketFactory().createSocket();
        s.connect(new InetSocketAddress(host, port), 5000);
        s.startHandshake();
        return s;
    }

    private byte[] secret(String code, RSAPublicKey client, RSAPublicKey server) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(trim(client.getModulus().toByteArray()));
        digest.update(trim(client.getPublicExponent().toByteArray()));
        digest.update(trim(server.getModulus().toByteArray()));
        digest.update(trim(server.getPublicExponent().toByteArray()));
        digest.update(new BigInteger(code.substring(2), 16).toByteArray());
        return digest.digest();
    }

    private byte[] trim(byte[] value) { return value.length > 1 && value[0] == 0 ? Arrays.copyOfRange(value, 1, value.length) : value; }
    private void writePairing(PairingProto.OuterMessage msg) throws Exception { frame(pairingOut, msg.toByteArray()); }
    private PairingProto.OuterMessage readPairing() throws Exception { return PairingProto.OuterMessage.parseFrom(read(pairingIn)); }
    private void writeRemote(RemoteProto.RemoteMessage msg) throws Exception { frame(remoteOut, msg.toByteArray()); }
    private void frame(OutputStream out, byte[] payload) throws Exception {
        writeVarint(out, payload.length);
        out.write(payload);
        out.flush();
    }
    private byte[] read(InputStream in) throws Exception {
        int n = readVarint(in);
        byte[] b = new byte[n];
        int o = 0;
        while (o < n) {
            int r = in.read(b, o, n - o);
            if (r < 0) throw new EOFException();
            o += r;
        }
        return b;
    }
    private void writeVarint(OutputStream out, int value) throws Exception {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }
    private int readVarint(InputStream in) throws Exception {
        int value = 0;
        int shift = 0;
        while (shift < 32) {
            int b = in.read();
            if (b < 0) throw new EOFException();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
            shift += 7;
        }
        throw new IOException("Malformed varint");
    }
}
