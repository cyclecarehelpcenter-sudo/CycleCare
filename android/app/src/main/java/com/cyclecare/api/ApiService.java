package com.cyclecare.api;

import com.cyclecare.models.ApiResponse;
import com.cyclecare.models.PeriodLog;
import com.cyclecare.models.Product;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    @POST("auth/register")
    Call<ApiResponse<Void>> register(@Body Map<String, String> body);

    @POST("auth/login")
    Call<ApiResponse<Void>> login(@Body Map<String, String> body);

    @GET("cycle")
    Call<Map<String, Object>> getCycleData();

    @GET("cycle/history")
    Call<Map<String, Object>> getCycleHistory();

    @GET("cycle/prediction")
    Call<Map<String, Object>> getCyclePrediction();

    @POST("cycle/period")
    Call<ApiResponse<PeriodLog>> addPeriodLog(@Body PeriodLog periodLog);

    @DELETE("cycle/period/{id}")
    Call<ApiResponse<Void>> deletePeriodLog(@Path("id") String id);

    @GET("symptoms")
    Call<Map<String, Object>> getSymptoms();

    @POST("symptoms/log")
    Call<Map<String, Object>> logSymptom(@Body Map<String, Object> body);

    @GET("moods")
    Call<Map<String, Object>> getMoods();

    @POST("moods/log")
    Call<Map<String, Object>> logMood(@Body Map<String, Object> body);

    @GET("reminders")
    Call<Map<String, Object>> getReminders();

    @POST("reminders")
    Call<Map<String, Object>> createReminder(@Body Map<String, Object> body);

    @GET("store/products")
    Call<Map<String, Object>> getProducts(@Query("category_id") String categoryId, @Query("search") String search);

    @GET("products/{id}")
    Call<Map<String, Object>> getProductDetails(@Path("id") String id);

    @GET("store/products/{id}")
    Call<Map<String, Object>> getStoreProductDetails(@Path("id") String id);

    @GET("store/cart")
    Call<Map<String, Object>> getCart();

    @POST("store/cart/items")
    Call<Map<String, Object>> addToCart(@Body Map<String, Object> body);

    @GET("care-kits")
    Call<Map<String, Object>> getCareKits();

    @POST("ai/chat")
    Call<Map<String, Object>> askAI(@Body Map<String, Object> body);
}
