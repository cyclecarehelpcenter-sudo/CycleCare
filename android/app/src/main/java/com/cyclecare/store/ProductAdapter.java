package com.cyclecare.store;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cyclecare.R;
import com.cyclecare.models.Product;

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

        holder.btnAddToKit.setOnClickListener(v -> 
            Toast.makeText(context, product.getName() + " added to Care Kit!", Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public int getItemCount() {
        return productList != null ? productList.size() : 0;
    }

    static class ProductViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDescription, tvPrice, tvCategoryTag, tvStockStatus;
        Button btnAddToKit;

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_product_name);
            tvDescription = itemView.findViewById(R.id.tv_product_description);
            tvPrice = itemView.findViewById(R.id.tv_product_price);
            tvCategoryTag = itemView.findViewById(R.id.tv_category_tag);
            tvStockStatus = itemView.findViewById(R.id.tv_stock_status);
            btnAddToKit = itemView.findViewById(R.id.btn_add_to_kit);
        }
    }
}
