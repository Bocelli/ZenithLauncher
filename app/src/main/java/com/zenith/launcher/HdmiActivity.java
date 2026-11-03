package com.zenith.launcher;

import android.app.Activity;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class HdmiActivity extends Activity {
    private TvView tvView;
    private View llStatusContainer;
    private TextView tvHdmiTitle;
    private TextView tvHdmiStatus;
    private String inputId;
    private String inputTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_hdmi);

        tvView = findViewById(R.id.tv_view);
        llStatusContainer = findViewById(R.id.ll_status_container);
        tvHdmiTitle = findViewById(R.id.tv_hdmi_title);
        tvHdmiStatus = findViewById(R.id.tv_hdmi_status);

        inputId = getIntent().getStringExtra("input_id");
        inputTitle = getIntent().getStringExtra("input_title");

        if (inputTitle != null && !inputTitle.isEmpty()) {
            tvHdmiTitle.setText(inputTitle);
        } else {
            tvHdmiTitle.setText("HDMI");
        }

        if (inputId == null || inputId.isEmpty()) {
            Toast.makeText(this, "Porta HDMI non specificata", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvHdmiStatus.setText("Connessione in corso...");
        llStatusContainer.setVisibility(View.VISIBLE);

        tvView.setCallback(new TvView.TvInputCallback() {
            @Override
            public void onVideoAvailable(String inId) {
                if (llStatusContainer != null) {
                    llStatusContainer.setVisibility(View.GONE);
                }
            }

            @Override
            public void onVideoUnavailable(String inId, int reason) {
                if (llStatusContainer != null) {
                    tvHdmiStatus.setText("Nessun segnale rilevato.");
                    llStatusContainer.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onContentBlocked(String inId, TvContentRating rating) {
                if (llStatusContainer != null) {
                    tvHdmiStatus.setText("Contenuto bloccato dal Parental Control.");
                    llStatusContainer.setVisibility(View.VISIBLE);
                }
            }
        });

        tune();
    }

    private void tune() {
        if (tvView != null && inputId != null) {
            try {
                Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
                tvView.setStreamVolume(1.0f);
                tvView.tune(inputId, uri);
            } catch (Throwable t) {
                tvHdmiStatus.setText("Errore apertura sorgente: " + t.getMessage());
                llStatusContainer.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (tvView != null) {
            tvView.reset();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tvView != null) {
            tvView.reset();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                finish();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
