package com.cyclecare.chat;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cyclecare.R;
import com.cyclecare.api.ApiClient;
import com.cyclecare.api.ApiService;
import com.cyclecare.store.CartActivity;
import com.cyclecare.models.ChatMessage;
import com.cyclecare.models.CircleContact;
import com.cyclecare.models.QuickCareItem;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CircleChatActivity extends AppCompatActivity {

    public static final String EXTRA_CONNECTION_ID = "extra_connection_id";
    public static final String EXTRA_CONTACT_NAME = "extra_contact_name";
    public static final String EXTRA_RELATIONSHIP = "extra_relationship";
    public static final String EXTRA_PARTNER_USER_ID = "extra_partner_user_id";

    private String connectionId = "demo-circle-connection";
    private String contactName = "Aman";
    private String relationship = "Husband";
    private String partnerUserId = "demo-partner-id";

    private RecyclerView rvMessages;
    private ChatAdapter chatAdapter;
    private final List<ChatMessage> messageList = new ArrayList<>();
    private final List<QuickCareItem> quickCareItems = new ArrayList<>();

    private EditText etChatMessage;
    private ImageButton btnChatSend, btnAttachCare, btnChatBack, btnChatStore;
    private TextView tvContactName, tvRelationshipBadge, tvAvatarLetter;

    private ApiService apiService;
    private Handler pollingHandler;
    private Runnable pollingRunnable;
    private final Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_circle_chat);

        apiService = ApiClient.getApiService(this);
        pollingHandler = new Handler(Looper.getMainLooper());

        if (getIntent().hasExtra(EXTRA_CONNECTION_ID)) {
            connectionId = getIntent().getStringExtra(EXTRA_CONNECTION_ID);
        }
        if (getIntent().hasExtra(EXTRA_CONTACT_NAME)) {
            contactName = getIntent().getStringExtra(EXTRA_CONTACT_NAME);
        }
        if (getIntent().hasExtra(EXTRA_RELATIONSHIP)) {
            relationship = getIntent().getStringExtra(EXTRA_RELATIONSHIP);
        }
        if (getIntent().hasExtra(EXTRA_PARTNER_USER_ID)) {
            partnerUserId = getIntent().getStringExtra(EXTRA_PARTNER_USER_ID);
        }

        initViews();
        setupRecyclerView();
        loadCatalog();

        if (connectionId == null || connectionId.equals("demo-circle-connection")) {
            resolveRealConnectionAndLoad();
        } else {
            loadMessages();
        }

        startPolling();
    }

    private void resolveRealConnectionAndLoad() {
        apiService.getCircleContacts().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Object contactsObj = response.body().get("contacts");
                    if (contactsObj != null) {
                        Type type = new TypeToken<List<CircleContact>>() {}.getType();
                        List<CircleContact> list = gson.fromJson(gson.toJson(contactsObj), type);
                        if (list != null && !list.isEmpty()) {
                            // Find Aman if possible, or take the first real contact
                            CircleContact target = list.get(0);
                            for (CircleContact c : list) {
                                if (c.getDisplayName().toLowerCase().contains("aman")) {
                                    target = c;
                                    break;
                                }
                            }
                            connectionId = target.getConnectionId();
                            contactName = target.getDisplayName();
                            relationship = target.getRelationship();
                            partnerUserId = target.getUserId();

                            updateHeaderUI();
                            loadMessages();
                            return;
                        }
                    }
                }
                loadMessages();
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                loadMessages();
            }
        });
    }

    private void updateHeaderUI() {
        if (tvContactName != null) tvContactName.setText(contactName);
        if (tvRelationshipBadge != null) tvRelationshipBadge.setText(relationship + " ❤️");
        if (tvAvatarLetter != null) tvAvatarLetter.setText(contactName.isEmpty() ? "P" : contactName.substring(0, 1).toUpperCase());
        if (chatAdapter != null) chatAdapter.notifyDataSetChanged();
    }

    private void initViews() {
        btnChatBack = findViewById(R.id.btn_chat_back);
        btnChatStore = findViewById(R.id.btn_chat_store);
        tvContactName = findViewById(R.id.tv_chat_contact_name);
        tvRelationshipBadge = findViewById(R.id.tv_chat_relationship_badge);
        tvAvatarLetter = findViewById(R.id.tv_contact_avatar_letter);
        rvMessages = findViewById(R.id.rv_chat_messages);
        etChatMessage = findViewById(R.id.et_chat_message);
        btnChatSend = findViewById(R.id.btn_chat_send);
        btnAttachCare = findViewById(R.id.btn_attach_care);

        tvContactName.setText(contactName);
        tvRelationshipBadge.setText(relationship + " ❤️");
        tvAvatarLetter.setText(contactName.isEmpty() ? "P" : contactName.substring(0, 1).toUpperCase());

        btnChatBack.setOnClickListener(v -> finish());
        btnChatStore.setOnClickListener(v -> startActivity(new Intent(this, CartActivity.class)));

        btnChatSend.setOnClickListener(v -> sendTextMessage());
        btnAttachCare.setOnClickListener(v -> showCareItemBottomSheet());
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter(this, messageList, contactName);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rvMessages.setLayoutManager(lm);
        rvMessages.setAdapter(chatAdapter);
    }

    private void loadCatalog() {
        apiService.getQuickCareItems().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Object itemsObj = response.body().get("items");
                    if (itemsObj != null) {
                        Type type = new TypeToken<List<QuickCareItem>>() {}.getType();
                        List<QuickCareItem> items = gson.fromJson(gson.toJson(itemsObj), type);
                        if (items != null && !items.isEmpty()) {
                            quickCareItems.clear();
                            quickCareItems.addAll(items);
                        }
                    }
                }
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
        });
    }

    private void loadMessages() {
        apiService.getChatMessages(connectionId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Object msgObj = response.body().get("messages");
                    if (msgObj != null) {
                        Type type = new TypeToken<List<ChatMessage>>() {}.getType();
                        List<ChatMessage> fetched = gson.fromJson(gson.toJson(msgObj), type);
                        if (fetched != null) {
                            messageList.clear();
                            messageList.addAll(fetched);
                            chatAdapter.notifyDataSetChanged();
                            if (!messageList.isEmpty()) {
                                rvMessages.scrollToPosition(messageList.size() - 1);
                            }
                        }
                    }
                }
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
        });
    }

    private void sendTextMessage() {
        String text = etChatMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        etChatMessage.setText("");

        // Immediate optimistic UI update
        ChatMessage optimistic = new ChatMessage("local_" + System.currentTimeMillis(), text, true, "TEXT");
        messageList.add(optimistic);
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        rvMessages.scrollToPosition(messageList.size() - 1);

        Map<String, Object> body = new HashMap<>();
        body.put("connection_id", connectionId);
        body.put("receiver_id", partnerUserId);
        body.put("content", text);
        body.put("message_type", "TEXT");

        apiService.sendChatMessage(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                loadMessages();
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
        });
    }

    private void sendQuickCareItem(String name, int price, String category, String imageUrl, String note) {
        // Immediate optimistic UI card
        ChatMessage optimistic = new ChatMessage("local_" + System.currentTimeMillis(), note, true, "CARE_REQUEST");
        Map<String, Object> meta = new HashMap<>();
        meta.put("item_name", name);
        meta.put("item_price", price);
        meta.put("item_category", category);
        meta.put("item_image", imageUrl);
        meta.put("status", "REQUESTED");
        optimistic.setMetadata(meta);
        optimistic.setCreatedAt(new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date()));
        messageList.add(optimistic);
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        rvMessages.scrollToPosition(messageList.size() - 1);

        Map<String, Object> body = new HashMap<>();
        body.put("connection_id", connectionId);
        body.put("receiver_id", partnerUserId);
        body.put("item_name", name);
        body.put("item_price", price);
        body.put("item_category", category);
        body.put("item_image", imageUrl);
        body.put("is_request", true);
        body.put("note", note);

        Toast.makeText(this, "✓ Requesting " + name + "...", Toast.LENGTH_SHORT).show();

        apiService.sendChatCareItem(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                loadMessages();
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
        });
    }

    private void showCareItemBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.dialog_care_item_sheet, null);
        dialog.setContentView(sheetView);

        RecyclerView rvSheet = sheetView.findViewById(R.id.rv_sheet_care_items);
        rvSheet.setLayoutManager(new LinearLayoutManager(this));

        // Use cached quick items or fallback
        List<QuickCareItem> items = !quickCareItems.isEmpty() ? quickCareItems : getDefaultQuickItems();

        CareItemSheetAdapter adapter = new CareItemSheetAdapter(this, items, new CareItemSheetAdapter.OnCareActionListener() {
            @Override
            public void onRequest(QuickCareItem item) {
                dialog.dismiss();
                sendQuickCareItem(item.getName(), item.getPrice(), item.getCategory(), item.getImageUrl(),
                        "Could you please get me " + item.getName() + "? 🌸");
            }

            @Override
            public void onSend(QuickCareItem item) {
                dialog.dismiss();

                String note = "Sent " + item.getName() + " with love! Hope this helps ❤️";

                // Immediate optimistic UI card
                ChatMessage optimistic = new ChatMessage("local_" + System.currentTimeMillis(), note, true, "CARE_ITEM_SENT");
                Map<String, Object> meta = new HashMap<>();
                meta.put("item_name", item.getName());
                meta.put("item_price", item.getPrice());
                meta.put("item_category", item.getCategory());
                meta.put("item_image", item.getImageUrl());
                meta.put("status", "SENT");
                optimistic.setMetadata(meta);
                optimistic.setCreatedAt(new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date()));
                messageList.add(optimistic);
                chatAdapter.notifyItemInserted(messageList.size() - 1);
                rvMessages.scrollToPosition(messageList.size() - 1);

                // Send as gift/item to partner
                Map<String, Object> body = new HashMap<>();
                body.put("connection_id", connectionId);
                body.put("receiver_id", partnerUserId);
                body.put("item_name", item.getName());
                body.put("item_price", item.getPrice());
                body.put("item_category", item.getCategory());
                body.put("item_image", item.getImageUrl());
                body.put("is_request", false);
                body.put("note", note);

                Toast.makeText(CircleChatActivity.this, "✓ Sending " + item.getName() + "...", Toast.LENGTH_SHORT).show();

                apiService.sendChatCareItem(body).enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        loadMessages();
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
                });
            }
        });

        rvSheet.setAdapter(adapter);
        dialog.show();
    }

    private List<QuickCareItem> getDefaultQuickItems() {
        List<QuickCareItem> list = new ArrayList<>();
        list.add(new QuickCareItem("1", "CycleCare Organic Cotton Pads (Night)", "Period Care", 149, "https://images.unsplash.com/photo-1583947215259-38e31be8751f?auto=format&fit=crop&w=600&q=80", "Heavy flow pads"));
        list.add(new QuickCareItem("2", "Instant Warmth Heat Patch (Pack of 3)", "Comfort & Cramps", 199, "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?auto=format&fit=crop&w=600&q=80", "8 hr continuous warming"));
        list.add(new QuickCareItem("3", "Chamomile & Ginger Soothing Herbal Tea", "Soothing Teas", 180, "https://images.unsplash.com/photo-1597481499750-3e6b22637e12?auto=format&fit=crop&w=600&q=80", "Relieves bloating & cramp pain"));
        list.add(new QuickCareItem("4", "CycleCare Herbal Cramp Relief Roll-On", "Medical & Pain Relief", 249, "https://images.unsplash.com/photo-1608571423902-eed4a5ad8108?auto=format&fit=crop&w=600&q=80", "Fast botanical pain relief"));
        list.add(new QuickCareItem("5", "CycleCare Dark Comfort Chocolate (70%)", "Comfort Treats", 120, "https://images.unsplash.com/photo-1549007994-cb92caebd54b?auto=format&fit=crop&w=600&q=80", "Rich magnesium dark chocolate"));
        list.add(new QuickCareItem("6", "CycleCare Emergency SOS Care Kit", "Emergency & Medical", 499, "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?auto=format&fit=crop&w=600&q=80", "Complete discreet emergency kit"));
        return list;
    }

    private void startPolling() {
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                loadMessages();
                pollingHandler.postDelayed(this, 3500);
            }
        };
        pollingHandler.postDelayed(pollingRunnable, 3500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pollingHandler != null && pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
        }
    }
}
