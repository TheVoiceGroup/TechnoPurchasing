package com.thevoicegroup.technopurchasing;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchaseHistoryRecord;
import com.android.billingclient.api.PurchaseHistoryResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;

import java.util.ArrayList;
import java.util.List;

public class PurchaseHelper {

    private String TAG = "PurchaseHelper";
    private Context context;
    private BillingClient mBillingClient;
    private PurchaseHelperListener purchaseHelperListener;
    private boolean mIsServiceConnected;
    private int billingSetupResponseCode;

    public PurchaseHelper(Context context, PurchaseHelperListener purchaseHelperListener) {
        this.context = context;
        mBillingClient = BillingClient.newBuilder(context).setListener(getPurchaseUpdatedListener()).enablePendingPurchases().build();
        this.purchaseHelperListener = purchaseHelperListener;
        startConnection(getServiceConnectionRequest());
    }


    private void startConnection(Runnable onSuccessRequest) {
        mBillingClient.startConnection(new BillingClientStateListener() {

            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                Log.d(TAG, "onBillingSetupFinished: " + billingResult.getResponseCode());

                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    mIsServiceConnected = true;

                    billingSetupResponseCode = billingResult.getResponseCode();

                    if (onSuccessRequest != null) {
                        onSuccessRequest.run();
                    }
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                mIsServiceConnected = false;
                Log.d(TAG, "onBillingServiceDisconnected: ");
            }
        });
    }


    public boolean isServiceConnected() {
        return mIsServiceConnected;
    }

    public void endConnection() {
        if (mBillingClient != null && mBillingClient.isReady()) {
            mBillingClient.endConnection();
            mBillingClient = null;
        }
    }


    private Runnable getServiceConnectionRequest() {
        return () -> {
            if (purchaseHelperListener != null)
                purchaseHelperListener.onServiceConnected(billingSetupResponseCode);
        };
    }

    private boolean isSubscriptionSupported() {
        BillingResult result = mBillingClient.isFeatureSupported(BillingClient.SkuType.SUBS);
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK)
            Log.w(TAG, "isSubscriptionSupported() got an error response: " + result.getResponseCode());
        return result.getResponseCode() != BillingClient.BillingResponseCode.OK;
    }

    private boolean isInAppSupported() {
        BillingResult result = mBillingClient.isFeatureSupported(BillingClient.SkuType.INAPP);
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK)
            Log.w(TAG, "isInAppSupported() got an error response: " + result.getResponseCode());
        return result.getResponseCode() != BillingClient.BillingResponseCode.OK;
    }


    public void getPurchasedItems(@BillingClient.SkuType String skuType) {

        Runnable purchaseHistoryRequest = () -> {
            Purchase.PurchasesResult purchasesResult = mBillingClient.queryPurchases(skuType);

            mBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP, new PurchaseHistoryResponseListener() {
                @Override
                public void onPurchaseHistoryResponse(@NonNull BillingResult billingResult, @Nullable List<PurchaseHistoryRecord> list) {
                    if(billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        purchaseHelperListener.onPurchasehistoryResponse(list);
                    }
                }
            });

//            if (purchaseHelperListener != null)
//                purchaseHelperListener.onPurchasehistoryResponse(purchasesResult.getPurchasesList());
        };

        executeServiceRequest(purchaseHistoryRequest);
    }


    public void getSkuDetails(final List<String> skuList, @BillingClient.SkuType String skuType) {
        Runnable skuDetailsRequest = () -> {
            SkuDetailsParams skuParams;
            skuParams = SkuDetailsParams.newBuilder().setType(skuType).setSkusList(skuList).build();
            mBillingClient.querySkuDetailsAsync(skuParams, (billingResult, skuDetailsList) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {

                    if (purchaseHelperListener != null)
                        purchaseHelperListener.onSkuQueryResponse(skuDetailsList);

                }
            });
        };

        executeServiceRequest(skuDetailsRequest);

    }

    public void launchBillingFLow(@BillingClient.SkuType String skuType, String productId) {

        List<String> skuList = new ArrayList<>();
        skuList.add(productId);

        SkuDetailsParams skuDetailsParams = SkuDetailsParams.newBuilder()
                .setSkusList(skuList).setType(skuType).build();

        mBillingClient.querySkuDetailsAsync(skuDetailsParams,
                (billingResult, list) -> {
                    Runnable launchBillingRequest = () -> {
                        BillingFlowParams mBillingFlowParams;
                        mBillingFlowParams = BillingFlowParams.newBuilder()
                                .setSkuDetails(list.get(0))
                                .build();

                        mBillingClient.launchBillingFlow((Activity) context, mBillingFlowParams);

                    };

                    executeServiceRequest(launchBillingRequest);
                });

//        Runnable launchBillingRequest = () -> {
//            BillingFlowParams mBillingFlowParams;
//            mBillingFlowParams = BillingFlowParams.newBuilder()
//                    .setSkuDetails(skuDetailsParams.get)
//                    .setType(skuType)
//                    .build();
//
//            mBillingClient.launchBillingFlow((Activity) context, mBillingFlowParams);
//
//        };
//
//        executeServiceRequest(launchBillingRequest);

    }


    public void gotoManageSubscription() {
        String PACKAGE_NAME = context.getPackageName();
        Log.d(TAG, "gotoManageSubscription: " + PACKAGE_NAME);
        Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/account/subscriptions?package="+PACKAGE_NAME));
        context.startActivity(browserIntent);
    }


    private PurchasesUpdatedListener getPurchaseUpdatedListener() {
        return (result, purchases) -> {
            if (purchaseHelperListener != null)
                purchaseHelperListener.onPurchasesUpdated(result.getResponseCode(), purchases);
        };
    }


    private void executeServiceRequest(Runnable runnable) {
        if (mIsServiceConnected) {
            runnable.run();
        } else {
            startConnection(runnable);
        }
    }



    public interface PurchaseHelperListener {
        void onServiceConnected(@BillingClient.BillingResponseCode int resultCode);

        void onSkuQueryResponse(List<SkuDetails> skuDetails);

        void onPurchasehistoryResponse(List<PurchaseHistoryRecord> purchasedItems);

        void onPurchasesUpdated(int responseCode, @Nullable List<Purchase> purchases);
    }

}