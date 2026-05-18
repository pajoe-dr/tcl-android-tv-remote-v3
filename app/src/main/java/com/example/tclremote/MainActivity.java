package com.example.tclremote;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView statusText;
    private EditText hostInput;
    private EditText pairingCodeInput;
    private EditText textInput;
    private RemoteServiceClient client;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        statusText = findViewById(R.id.statusText);
        hostInput = findViewById(R.id.hostInput);
        pairingCodeInput = findViewById(R.id.pairingCodeInput);
        textInput = findViewById(R.id.textInput);
        bind(R.id.startPairingButton, v -> startPairing());
        bind(R.id.finishPairingButton, v -> finishPairing());
        bind(R.id.connectButton, v -> connect());
        bind(R.id.sendTextButton, v -> setStatus("Keyboard input is the next step after remote pairing works"));
        bindKey(R.id.volumeUpButton, 24);
        bindKey(R.id.volumeDownButton, 25);
        bindKey(R.id.channelUpButton, 166);
        bindKey(R.id.channelDownButton, 167);
        bindKey(R.id.muteButton, 164);
        bindKey(R.id.homeButton, 3);
        bindKey(R.id.backButton, 4);
        bindKey(R.id.menuButton, 82);
    }

    private void bind(int id, View.OnClickListener listener) { ((Button) findViewById(id)).setOnClickListener(listener); }
    private void bindKey(int id, int keyCode) { bind(id, v -> sendKey(keyCode)); }
    private void startPairing() { executor.execute(() -> { try { client = new RemoteServiceClient(CertificateStore.loadOrCreate(this)); client.startPairing(hostInput.getText().toString().trim()); setStatus("TV should now show a pairing code"); } catch (Exception e) { setStatus("Pairing error: " + e.getMessage()); } }); }
    private void finishPairing() { executor.execute(() -> { try { client.finishPairing(pairingCodeInput.getText().toString().trim()); setStatus("Paired successfully"); } catch (Exception e) { setStatus("Pairing error: " + e.getMessage()); } }); }
    private void connect() { executor.execute(() -> { try { if (client == null) client = new RemoteServiceClient(CertificateStore.loadOrCreate(this)); client.connect(hostInput.getText().toString().trim()); setStatus("Connected"); } catch (Exception e) { setStatus("Connection error: " + e.getMessage()); } }); }
    private void sendKey(int keyCode) { executor.execute(() -> { try { client.sendKey(keyCode); setStatus("Sent key " + keyCode); } catch (Exception e) { setStatus("Command error: " + e.getMessage()); } }); }
    private void setStatus(String text) { runOnUiThread(() -> statusText.setText(text)); }
    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
