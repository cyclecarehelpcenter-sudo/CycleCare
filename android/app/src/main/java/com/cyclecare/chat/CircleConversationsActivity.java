package com.cyclecare.chat;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.cyclecare.R;
import com.cyclecare.api.ApiClient;
import com.cyclecare.api.ApiService;
import com.cyclecare.models.CircleContact;
import com.cyclecare.store.CartActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
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

public class CircleConversationsActivity extends AppCompatActivity {

    private RecyclerView rvConversations;
    private CircleConversationsAdapter adapter;
    private final List<CircleContact> contactList = new ArrayList<>();
    private final List<CircleContact> filteredList = new ArrayList<>();

    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout layoutEmpty;
    private EditText etSearch;
    private ExtendedFloatingActionButton fabNewChat;
    private ImageButton btnBack, btnStore, btnRefresh;

    private ApiService apiService;
    private Handler autoRefreshHandler;
    private Runnable autoRefreshRunnable;
    private final Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_circle_conversations);

        apiService = ApiClient.getApiService(this);
        autoRefreshHandler = new Handler(Looper.getMainLooper());

        initViews();
        setupRecyclerView();
        setupSearchFilter();
        loadContacts(true);
        startAutoRefresh();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_conv_back);
        btnStore = findViewById(R.id.btn_conv_store);
        btnRefresh = findViewById(R.id.btn_conv_refresh);
        etSearch = findViewById(R.id.et_search_contacts);
        swipeRefresh = findViewById(R.id.swipe_refresh_conversations);
        rvConversations = findViewById(R.id.rv_conversations);
        layoutEmpty = findViewById(R.id.layout_empty_conversations);
        fabNewChat = findViewById(R.id.fab_new_chat);

        btnBack.setOnClickListener(v -> finish());
        btnStore.setOnClickListener(v -> startActivity(new Intent(this, CartActivity.class)));
        btnRefresh.setOnClickListener(v -> loadContacts(false));
        swipeRefresh.setOnRefreshListener(() -> loadContacts(false));

        fabNewChat.setOnClickListener(v -> showNewChatBottomSheet());
    }

    private void setupRecyclerView() {
        adapter = new CircleConversationsAdapter(this, filteredList, contact -> {
            Intent intent = new Intent(CircleConversationsActivity.this, CircleChatActivity.class);
            intent.putExtra(CircleChatActivity.EXTRA_CONNECTION_ID, contact.getConnectionId());
            intent.putExtra(CircleChatActivity.EXTRA_CONTACT_NAME, contact.getDisplayName());
            intent.putExtra(CircleChatActivity.EXTRA_RELATIONSHIP, contact.getRelationship());
            intent.putExtra(CircleChatActivity.EXTRA_PARTNER_USER_ID, contact.getUserId());
            startActivity(intent);
        });

        LinearLayoutManager lm = new LinearLayoutManager(this);
        rvConversations.setLayoutManager(lm);
        rvConversations.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        rvConversations.setAdapter(adapter);
    }

    private void setupSearchFilter() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterContacts(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterContacts(String query) {
        filteredList.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredList.addAll(contactList);
        } else {
            String lower = query.trim().toLowerCase();
            for (CircleContact c : contactList) {
                if (c.getDisplayName().toLowerCase().contains(lower) ||
                    c.getRelationship().toLowerCase().contains(lower) ||
                    c.getLastMessage().toLowerCase().contains(lower)) {
                    filteredList.add(c);
                }
            }
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void loadContacts(boolean showLoading) {
        if (showLoading) swipeRefresh.setRefreshing(true);

        apiService.getCircleContacts().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                swipeRefresh.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    Object contactsObj = response.body().get("contacts");
                    if (contactsObj != null) {
                        Type type = new TypeToken<List<CircleContact>>() {}.getType();
                        List<CircleContact> fetched = gson.fromJson(gson.toJson(contactsObj), type);
                        if (fetched != null) {
                            contactList.clear();
                            contactList.addAll(fetched);
                            filterContacts(etSearch.getText().toString());
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                swipeRefresh.setRefreshing(false);
            }
        });
    }

    private void updateEmptyState() {
        if (filteredList.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            rvConversations.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            rvConversations.setVisibility(View.VISIBLE);
        }
    }

    private void showNewChatBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.dialog_care_item_sheet, null); // Reuse styled sheet container or build custom
        
        // Build a sleek dialog to search and chat
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("💬 Start New Circle Care Chat");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        TextView tvHint = new TextView(this);
        tvHint.setText("Enter registered user's email or name (e.g. Aman, Admin, etc.):");
        tvHint.setTextColor(getResources().getColor(R.color.textSecondary));
        tvHint.setTextSize(13);
        layout.addView(tvHint);

        EditText etQuery = new EditText(this);
        etQuery.setHint("email@cyclecare.app or name");
        etQuery.setTextSize(15);
        layout.addView(etQuery);

        TextView tvTagPrompt = new TextView(this);
        tvTagPrompt.setText("Assign Relationship Tag:");
        tvTagPrompt.setTextColor(getResources().getColor(R.color.textSecondary));
        tvTagPrompt.setTextSize(13);
        tvTagPrompt.setPadding(0, 24, 0, 8);
        layout.addView(tvTagPrompt);

        EditText etTag = new EditText(this);
        etTag.setHint("e.g. Husband, Wife, Sister, Best Friend");
        etTag.setText("Husband ❤️");
        etTag.setTextSize(14);
        layout.addView(etTag);

        builder.setView(layout);

        builder.setPositiveButton("Search & Chat 🚀", (d, which) -> {
            String q = etQuery.getText().toString().trim();
            String tag = etTag.getText().toString().trim();
            if (q.isEmpty()) return;

            Toast.makeText(this, "Searching for " + q + "...", Toast.LENGTH_SHORT).show();
            apiService.searchUsers(q).enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        List<?> users = (List<?>) response.body().get("users");
                        if (users != null && !users.isEmpty()) {
                            Map<?, ?> u = (Map<?, ?>) users.get(0);
                            String targetId = String.valueOf(u.get("id"));
                            String targetName = String.valueOf(u.get("display_name"));

                            // Update Tag
                            Map<String, String> tagBody = new HashMap<>();
                            tagBody.put("target_user_id", targetId);
                            tagBody.put("tag", tag);
                            apiService.setContactTag(tagBody).enqueue(new Callback<Map<String, Object>>() {
                                @Override
                                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                                    // Open Chat
                                    Intent intent = new Intent(CircleConversationsActivity.this, CircleChatActivity.class);
                                    intent.putExtra(CircleChatActivity.EXTRA_CONTACT_NAME, targetName);
                                    intent.putExtra(CircleChatActivity.EXTRA_RELATIONSHIP, tag);
                                    intent.putExtra(CircleChatActivity.EXTRA_PARTNER_USER_ID, targetId);
                                    startActivity(intent);
                                    loadContacts(false);
                                }

                                @Override
                                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                                    Intent intent = new Intent(CircleConversationsActivity.this, CircleChatActivity.class);
                                    intent.putExtra(CircleChatActivity.EXTRA_CONTACT_NAME, targetName);
                                    intent.putExtra(CircleChatActivity.EXTRA_RELATIONSHIP, tag);
                                    intent.putExtra(CircleChatActivity.EXTRA_PARTNER_USER_ID, targetId);
                                    startActivity(intent);
                                }
                            });
                        } else {
                            Toast.makeText(CircleConversationsActivity.this, "No user found with \"" + q + "\"", Toast.LENGTH_LONG).show();
                        }
                    }
                }

                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    Toast.makeText(CircleConversationsActivity.this, "Search error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void startAutoRefresh() {
        autoRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                loadContacts(false);
                autoRefreshHandler.postDelayed(this, 5000);
            }
        };
        autoRefreshHandler.postDelayed(autoRefreshRunnable, 5000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadContacts(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoRefreshHandler != null && autoRefreshRunnable != null) {
            autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        }
    }
}
