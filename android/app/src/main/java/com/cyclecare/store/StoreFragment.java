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
import com.cyclecare.models.Product;

import java.util.ArrayList;
import java.util.List;

public class StoreFragment extends Fragment {

    private RecyclerView rvProducts;
    private ProductAdapter adapter;
    private List<Product> allProducts = new ArrayList<>();
    private List<Product> displayedProducts = new ArrayList<>();

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

        loadSampleProducts();
        filterProducts("All");

        chipAll.setOnClickListener(v -> filterProducts("All"));
        chipPeriodCare.setOnClickListener(v -> filterProducts("Period Care"));
        chipComfort.setOnClickListener(v -> filterProducts("Comfort"));
        chipHygiene.setOnClickListener(v -> filterProducts("Hygiene"));
        chipTeas.setOnClickListener(v -> filterProducts("Teas"));

        return view;
    }

    private void loadSampleProducts() {
        allProducts.clear();
        allProducts.add(new Product("p1", "Organic Cotton Pads", "100% Organic biodegradable day pads (12x)", 299, "Period Care", 25));
        allProducts.add(new Product("p2", "Cramp Relief Heat Patch", "Continuous 8-hour soothing warmth therapy", 199, "Comfort", 40));
        allProducts.add(new Product("p3", "Chamomile Comfort Tea", "Natural caffeine-free relaxing herbal brew", 349, "Teas", 18));
        allProducts.add(new Product("p4", "pH Balanced Hygiene Wash", "Gentle aloe vera & tea tree daily wash", 279, "Hygiene", 30));
        allProducts.add(new Product("p5", "Cycle Care Travel Pouch", "Discreet waterproof travel storage pouch", 449, "Comfort", 15));
        allProducts.add(new Product("p6", "Heavy Flow Night Wings", "Extra long overnight absorption pads (10x)", 329, "Period Care", 50));
    }

    private void filterProducts(String category) {
        displayedProducts.clear();
        if (category.equals("All")) {
            displayedProducts.addAll(allProducts);
        } else {
            for (Product p : allProducts) {
                if (p.getCategory().equalsIgnoreCase(category)) {
                    displayedProducts.add(p);
                }
            }
        }
        if (adapter == null) {
            adapter = new ProductAdapter(getContext(), displayedProducts);
            rvProducts.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }
    }
}
