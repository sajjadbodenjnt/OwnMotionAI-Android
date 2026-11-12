package com.mira.ai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_SPEECH = 41;
    private static final int REQ_MIC = 42;
    private LinearLayout messages;
    private ScrollView scroll;
    private EditText input;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean femaleVoiceFound = false;
    private String voiceStatus = "ভয়েস প্রস্তুত হচ্ছে";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(12, 18, 34));
        getWindow().setNavigationBarColor(Color.rgb(12, 18, 34));
        buildUi();
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int languageResult = tts.setLanguage(new Locale("bn", "BD"));
                chooseFemaleVoice();
                ttsReady = true;
                if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                    languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    voiceStatus = "বাংলা TTS ভাষা ইনস্টল নেই";
                }
                Toast.makeText(this, voiceStatus, Toast.LENGTH_LONG).show();
            } else {
                voiceStatus = "Text-to-Speech চালু করা যায়নি";
                Toast.makeText(this, voiceStatus, Toast.LENGTH_LONG).show();
            }
        });
        addMessage("assistant", "আসসালামু আলাইকুম! আমি Mira AI। চ্যাট ও ভয়েস ইন্টারফেস প্রস্তুত। AI মডেল সংযুক্ত না হওয়া পর্যন্ত আমি বানানো উত্তর দেব না।");
    }

    private void chooseFemaleVoice() {
        if (Build.VERSION.SDK_INT < 21 || tts == null) {
            voiceStatus = "এই Android সংস্করণে ভয়েস নির্বাচন সমর্থিত নয়";
            return;
        }
        Set<Voice> voices = tts.getVoices();
        if (voices == null || voices.isEmpty()) {
            voiceStatus = "ইনস্টল করা ভয়েস পাওয়া যায়নি";
            return;
        }
        Voice bengaliFemale = null;
        Voice anyFemale = null;
        for (Voice voice : voices) {
            String name = voice.getName() == null ? "" : voice.getName().toLowerCase(Locale.ROOT);
            String language = voice.getLocale() == null ? "" : voice.getLocale().getLanguage();
            boolean female = name.contains("female") || name.contains("woman") ||
                    name.contains("fem") || name.contains("samantha") ||
                    name.contains("zira") || name.contains("susan");
            if (female && anyFemale == null) anyFemale = voice;
            if (female && "bn".equals(language)) {
                bengaliFemale = voice;
                break;
            }
        }
        Voice selected = bengaliFemale != null ? bengaliFemale : anyFemale;
        if (selected != null) {
            int result = tts.setVoice(selected);
            if (result == TextToSpeech.SUCCESS) {
                femaleVoiceFound = true;
                voiceStatus = bengaliFemale != null
                        ? "বাংলা নারী কণ্ঠ নির্বাচিত: " + selected.getName()
                        : "নারী কণ্ঠ নির্বাচিত; বাংলা উচ্চারণের মান ভিন্ন হতে পারে";
                return;
            }
        }
        tts.setLanguage(new Locale("bn", "BD"));
        femaleVoiceFound = false;
        voiceStatus = "ইনস্টল করা নারী কণ্ঠ পাওয়া যায়নি; ডিভাইসের ডিফল্ট বাংলা কণ্ঠ ব্যবহৃত হবে";
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(12, 18, 34));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(16, 12, 12, 12);
        header.setBackgroundColor(Color.rgb(20, 29, 51));
        TextView brand = new TextView(this);
        brand.setText("✦  Mira AI"); brand.setTextColor(Color.WHITE);
        brand.setTextSize(22); brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button voiceSettings = new Button(this);
        voiceSettings.setText("♀ Voice");
        voiceSettings.setOnClickListener(v -> testVoice());
        header.addView(voiceSettings);
        root.addView(header);

        scroll = new ScrollView(this);
        messages = new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(14, 18, 14, 18);
        scroll.addView(messages);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView hint = new TextView(this);
        hint.setText("Mira AI • বাংলা ও English • নারী কণ্ঠ পছন্দ");
        hint.setTextColor(Color.LTGRAY); hint.setTextSize(11);
        hint.setGravity(Gravity.CENTER); hint.setPadding(8, 4, 8, 8);
        root.addView(hint);

        LinearLayout composer = new LinearLayout(this);
        composer.setPadding(10, 8, 10, 12); composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setBackgroundColor(Color.rgb(20, 29, 51));
        input = new EditText(this); input.setSingleLine(false); input.setMinLines(1); input.setMaxLines(4);
        input.setHint("এখানে লিখুন…"); input.setTextColor(Color.WHITE); input.setHintTextColor(Color.GRAY);
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(100, 130, 190)));
        composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        Button mic = new Button(this); mic.setText("🎙"); mic.setOnClickListener(v -> startVoice());
        composer.addView(mic);
        Button send = new Button(this); send.setText("➤"); send.setOnClickListener(v -> sendMessage());
        composer.addView(send);
        root.addView(composer);
    }

    private void testVoice() {
        if (!ttsReady || tts == null) {
            Toast.makeText(this, "ভয়েস ইঞ্জিন এখনো প্রস্তুত নয়", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, voiceStatus, Toast.LENGTH_LONG).show();
        tts.speak("আসসালামু আলাইকুম। আমি মীরা। আপনার সহকারী হিসেবে প্রস্তুত আছি।",
                TextToSpeech.QUEUE_FLUSH, null, "mira_voice_test");
    }

    private void addMessage(String who, String body) {
        TextView bubble = new TextView(this);
        bubble.setText((who.equals("user") ? "আপনি\n" : "Mira AI\n") + body);
        bubble.setTextColor(Color.WHITE); bubble.setTextSize(15); bubble.setPadding(16, 12, 16, 12);
        bubble.setBackgroundColor(who.equals("user") ? Color.rgb(39, 75, 126) : Color.rgb(31, 42, 65));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, 12); bubble.setLayoutParams(lp);
        messages.addView(bubble);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void sendMessage() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        addMessage("user", text); input.setText("");
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
        String reply = "আপনার বার্তাটি পেয়েছি। এই প্রাথমিক সংস্করণে এখনো Gemma/llama.cpp মডেল সংযুক্ত হয়নি। পরবর্তী ধাপে স্থানীয় GGUF inference engine যুক্ত করতে হবে।";
        addMessage("assistant", reply);
    }

    private void startVoice() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC); return;
        }
        launchRecognizer();
    }

    private void launchRecognizer() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD");
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Mira AI-কে বলুন");
            startActivityForResult(intent, REQ_SPEECH);
        } catch (Exception e) {
            Toast.makeText(this, "এই ডিভাইসে Speech Recognition পাওয়া যায়নি", Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_MIC && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) launchRecognizer();
        else if (requestCode == REQ_MIC) Toast.makeText(this, "ভয়েস ইনপুটের জন্য মাইক্রোফোন অনুমতি প্রয়োজন", Toast.LENGTH_SHORT).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SPEECH && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) { input.setText(results.get(0)); sendMessage(); }
        }
    }

    @Override protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
