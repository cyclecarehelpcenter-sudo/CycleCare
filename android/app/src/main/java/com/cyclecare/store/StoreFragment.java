package com.cyclecare.store;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cyclecare.R;
import com.cyclecare.api.ApiClient;
import com.cyclecare.models.Product;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StoreFragment extends Fragment {

    private RecyclerView rvProducts;
    private ProductAdapter adapter;
    private List<Product> allProducts = new ArrayList<>();
    private List<Product> displayedProducts = new ArrayList<>();
    private String currentCategoryFilter = "All";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_store, container, false);

        rvProducts = view.findViewById(R.id.rv_products);
        rvProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));

        Button chipAll = view.findViewById(R.id.chip_all);
        Button chipPeriodCare = view.findViewById(R.id.chip_period_care);
        Button chipComfort = view.findViewById(R.id.chip_comfort);
        Button chipHygiene = view.findViewById(R.id.chip_hygiene);
        Button chipTeas = view.findViewById(R.id.chip_teas);

        adapter = new ProductAdapter(getContext(), displayedProducts);
        rvProducts.setAdapter(adapter);

        loadSampleProducts();
        filterProducts("All");

        // Fetch live catalog from server
        fetchLiveProducts();

        chipAll.setOnClickListener(v -> filterProducts("All"));
        chipPeriodCare.setOnClickListener(v -> filterProducts("Period Care"));
        chipComfort.setOnClickListener(v -> filterProducts("Comfort"));
        chipHygiene.setOnClickListener(v -> filterProducts("Hygiene"));
        chipTeas.setOnClickListener(v -> filterProducts("Teas"));

        // Header Action Listeners
        View btnOrders = view.findViewById(R.id.btn_store_orders);
        if (btnOrders != null) {
            btnOrders.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(getContext(), OrdersActivity.class);
                startActivity(intent);
            });
        }

        View btnCart = view.findViewById(R.id.btn_store_cart);
        if (btnCart != null) {
            btnCart.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(getContext(), CartActivity.class);
                startActivity(intent);
            });
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateCartBadge();
    }

    private void updateCartBadge() {
        if (getView() == null || getContext() == null) return;
        android.widget.TextView tvBadge = getView().findViewById(R.id.tv_store_cart_badge);
        if (tvBadge == null) return;

        ApiClient.getApiService(getContext()).getCart().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> body = response.body();
                    Object itemsObj = body.get("items");
                    if (itemsObj instanceof List) {
                        int count = ((List<?>) itemsObj).size();
                        if (count > 0) {
                            tvBadge.setVisibility(View.VISIBLE);
                            tvBadge.setText(String.valueOf(count));
                        } else {
                            tvBadge.setVisibility(View.GONE);
                        }
                    }
                }
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
        });
    }

    private void fetchLiveProducts() {
        if (getContext() == null) return;
        ApiClient.getApiService(getContext()).getProducts(null, null).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> body = response.body();
                    Object productsObj = body.get("products");
                    if (productsObj != null) {
                        Gson gson = new Gson();
                        String json = gson.toJson(productsObj);
                        Type listType = new TypeToken<List<Product>>() {}.getType();
                        List<Product> fetched = gson.fromJson(json, listType);
                        if (fetched != null && !fetched.isEmpty()) {
                            allProducts.clear();
                            allProducts.addAll(fetched);
                            filterProducts(currentCategoryFilter);
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // Keep sample products visible
            }
        });
    }

    private void loadSampleProducts() {
        allProducts.clear();
        allProducts.add(new Product("a1111111-1111-1111-1111-111111111111", "CycleCare Organic Cotton Pads (Night)", "Soft, ultra-absorbent organic cotton pads with heavy flow leak guards (10x)", 149, "Period Care", 100, "https://images.unsplash.com/photo-1583947215259-38e31be8751f?auto=format&fit=crop&w=600&q=80"));
        allProducts.add(new Product("a2222222-2222-2222-2222-222222222222", "CycleCare Ultra-Thin Daily Pantyliners", "Breathable daily pantyliners for all-day fresh comfort (20x)", 99, "Period Care", 150, "https://images.unsplash.com/photo-1584017911766-d451b3d0e843?auto=format&fit=crop&w=600&q=80"));
        allProducts.add(new Product("b1111111-1111-1111-1111-111111111111", "Instant Warmth Heat Patch (Pack of 3)", "Air-activated heating patches soothing cramps for up to 8 hours", 199, "Comfort", 80, "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?auto=format&fit=crop&w=600&q=80"));
        allProducts.add(new Product("b2222222-2222-2222-2222-222222222222", "Soothing Electric Heating Water Bag", "Rechargeable electric hot water bag for abdominal & back relief", 399, "Comfort", 40, "https://images.unsplash.com/photo-1515377905703-c4788e51af15?auto=format&fit=crop&w=600&q=80"));
        allProducts.add(new Product("c1111111-1111-1111-1111-111111111111", "Gentle pH-Balanced Intimate Wipes", "Biodegradable wipes with soothing aloe vera and chamomile (15x)", 85, "Hygiene", 120, "https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=600&q=80"));
        allProducts.add(new Product("d1111111-1111-1111-1111-111111111111", "CycleCare Period Comfort Dark Chocolate (70%)", "Rich Belgian dark chocolate infused with magnesium for cramp ease", 120, "Comfort", 200, "https://images.unsplash.com/photo-1549007994-cb92caebd54b?auto=format&fit=crop&w=600&q=80"));
        allProducts.add(new Product("d2222222-2222-2222-2222-222222222222", "Chamomile & Ginger Soothing Herbal Tea", "Caffeine-free herbal blend relieving bloating & body tension (15 Bags)", 180, "Teas", 90, "https://images.unsplash.com/photo-1597481499750-3e6b22637e12?auto=format&fit=crop&w=600&q=80"));
        allProducts.add(new Product("009f4b12-2efa-4e28-8c1a-4f872e955fb7", "CycleCare Herbal Cramp Relief Roll-On", "Ayurvedic botanical essential oils for fast abdominal cramp relief", 249, "Comfort", 65, "https://images.unsplash.com/photo-1608571423902-eed4a5ad8108?auto=format&fit=crop&w=600&q=80"));
        allProducts.add(new Product("1f970168-18c4-4b4a-81bd-3aed6fcf3587", "CycleCare Magnesium Sleep & Cramp Gummies", "Berry flavored chewable gummies supporting muscle relaxation & deep sleep", 349, "Comfort", 55, "https://images.unsplash.com/photo-1577401239170-897942555fb3?auto=format&fit=crop&w=600&q=80"));
    }

    private void filterProducts(String category) {
        currentCategoryFilter = category;
        displayedProducts.clear();
        if (category.equals("All")) {
            displayedProducts.addAll(allProducts);
        } else {
            for (Product p : allProducts) {
                String cat = p.getCategory();
                if (cat != null && (cat.toLowerCase().contains(category.toLowerCase()) || category.toLowerCase().contains(cat.toLowerCase()))) {
                    displayedProducts.add(p);
                }
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }
}
