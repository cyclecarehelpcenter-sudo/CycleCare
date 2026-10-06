package com.cyclecare.store;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cyclecare.R;
import com.cyclecare.models.Product;
import com.cyclecare.utils.ImageLoader;

import java.util.List;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    private final Context context;
    private final List<Product> productList;

    public ProductAdapter(Context context, List<Product> productList) {
        this.context = context;
        this.productList = productList;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_product, parent, false);
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product product = productList.get(position);
        holder.tvName.setText(product.getName());
        holder.tvDescription.setText(product.getDescription());
        holder.tvPrice.setText("₹ " + (int) product.getActivePrice());
        holder.tvCategoryTag.setText(product.getCategory());
        holder.tvStockStatus.setText(product.getStock() > 0 ? "In Stock" : "Restocking");

        if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
            ImageLoader.getInstance().loadImage(product.getImageUrl(), holder.ivProductIcon, R.drawable.ic_nav_store_3d);
        } else {
            holder.ivProductIcon.setImageResource(R.drawable.ic_nav_store_3d);
        }

        // Tapping product opens Product Detail page directly
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ProductDetailActivity.class);
            intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, product.getId());
            intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT_NAME, product.getName());
            intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT_PRICE, String.valueOf(product.getActivePrice()));
            intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT_IMAGE, product.getImageUrl());
            intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT_DESC, product.getDescription());
            context.startActivity(intent);
        });

        holder.btnAddToKit.setOnClickListener(v -> {
            Toast.makeText(context, "✓ " + product.getName() + " added to Care Kit!", Toast.LENGTH_SHORT).show();
            if (product.getId() != null) {
                java.util.Map<String, Object> cartItem = new java.util.HashMap<>();
                cartItem.put("product_id", product.getId());
                cartItem.put("quantity", 1);
                com.cyclecare.api.ApiClient.getApiService(context).addToCart(cartItem).enqueue(new retrofit2.Callback<java.util.Map<String, Object>>() {
                    @Override
                    public void onResponse(retrofit2.Call<java.util.Map<String, Object>> call, retrofit2.Response<java.util.Map<String, Object>> response) {}
                    @Override
                    public void onFailure(retrofit2.Call<java.util.Map<String, Object>> call, Throwable t) {}
                });
            }
        });
    }

    @Override
    public int getItemCount() {
        return productList != null ? productList.size() : 0;
    }

    static class ProductViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProductIcon;
        TextView tvName, tvDescription, tvPrice, tvCategoryTag, tvStockStatus;
        Button btnAddToKit;

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProductIcon = itemView.findViewById(R.id.iv_product_icon);
            tvName = itemView.findViewById(R.id.tv_product_name);
            tvDescription = itemView.findViewById(R.id.tv_product_description);
            tvPrice = itemView.findViewById(R.id.tv_product_price);
            tvCategoryTag = itemView.findViewById(R.id.tv_category_tag);
            tvStockStatus = itemView.findViewById(R.id.tv_stock_status);
            btnAddToKit = itemView.findViewById(R.id.btn_add_to_kit);
        }
    }
}
