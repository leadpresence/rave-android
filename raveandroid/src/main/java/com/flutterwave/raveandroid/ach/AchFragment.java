package com.flutterwave.raveandroid.ach;


import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.flutterwave.raveandroid.R;
import com.flutterwave.raveandroid.RavePayActivity;
import com.flutterwave.raveandroid.RavePayInitializer;
import com.flutterwave.raveandroid.WebFragment;
import com.flutterwave.raveandroid.data.events.ErrorEvent;
import com.flutterwave.raveandroid.data.events.FeeDisplayResponseEvent;
import com.flutterwave.raveandroid.data.events.InstructionsDisplayedEvent;
import com.flutterwave.raveandroid.data.events.StartTypingEvent;
import com.flutterwave.raveandroid.di.modules.AchModule;
import com.flutterwave.raveandroid.responses.RequeryResponse;
import com.flutterwave.raveandroid.verification.VerificationActivity;

import javax.inject.Inject;


/**
 * A simple {@link Fragment} subclass.
 */
public class AchFragment extends Fragment implements AchContract.View, View.OnClickListener
        , View.OnFocusChangeListener {

    @Inject
    AchPresenter presenter;

    private View v;
    private Button payButton;
    private TextInputLayout amountTil;
    private TextInputEditText amountEt;
    private TextView payInstructionsTv;
    private ProgressDialog progressDialog;
    private RavePayInitializer ravePayInitializer;

    public static final int FOR_ACH = 892;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        injectComponents();

        v = inflater.inflate(R.layout.fragment_ach, container, false);

        initializeViews();

        setListeners();

        initializePresenter();

        return v;
    }


    private void injectComponents() {

        if (getActivity() != null) {
            ((RavePayActivity) getActivity()).getAppComponent()
                    .plus(new AchModule(this))
                    .inject(this);
        }
    }

    private void initializePresenter() {
        if (getActivity() != null) {
            ravePayInitializer = ((RavePayActivity) getActivity()).getRavePayInitializer();
            presenter.init(ravePayInitializer);
        }
    }

    private void setListeners() {
        payButton.setOnClickListener(this);

        amountEt.setOnFocusChangeListener(this);
    }

    private void initializeViews() {
        payInstructionsTv = v.findViewById(R.id.paymentInstructionsTv);
        payButton = v.findViewById(R.id.rave_payButton);
        amountTil = v.findViewById(R.id.rave_amountTil);
        amountEt = v.findViewById(R.id.rave_amountEt);
    }

    @Override
    public void onClick(View view) {
        int i = view.getId();
        if (i == R.id.rave_payButton) {
            String amount = amountEt.getText().toString();
            presenter.onDataCollected(ravePayInitializer, amount);
        }
    }


    @Override
    public void showFee(final String authUrl, final String flwRef, final String charge_amount, final String currency) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(getResources().getString(R.string.charge) + " " + charge_amount + " " + currency + getResources().getString(R.string.askToContinue));
        builder.setPositiveButton(getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                presenter.logEvent(new FeeDisplayResponseEvent(true).getEvent(), ravePayInitializer.getPublicKey());

                presenter.onFeeConfirmed(authUrl, flwRef);

            }
        }).setNegativeButton(getResources().getString(R.string.no), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                presenter.logEvent(new FeeDisplayResponseEvent(false).getEvent(), ravePayInitializer.getPublicKey());
            }
        });

        builder.show();

    }

    @Override
    public void onAmountValidated(String amountToSet, int visibility) {
        amountTil.setVisibility(visibility);
        amountEt.setText(amountToSet);
    }

    @Override
    public void onValidationSuccessful(String amount) {
        presenter.processTransaction(amount, ravePayInitializer, ravePayInitializer.getIsDisplayFee());
    }

    @Override
    public void showRedirectMessage(boolean active) {

        if (active) {
            presenter.logEvent(new InstructionsDisplayedEvent("ACH").getEvent(), ravePayInitializer.getPublicKey());
            payInstructionsTv.setVisibility(View.VISIBLE);
        }
        else {
            payInstructionsTv.setVisibility(View.GONE);
        }
    }

    @Override
    public void showWebView(String authUrl, String flwRef) {

        Intent intent = new Intent(getContext(), VerificationActivity.class);
        intent.putExtra(VerificationActivity.PUBLIC_KEY_EXTRA, ravePayInitializer.getPublicKey());
        intent.putExtra(WebFragment.EXTRA_AUTH_URL, authUrl);
        intent.putExtra(VerificationActivity.ACTIVITY_MOTIVE,"web");
        intent.putExtra("theme",ravePayInitializer.getTheme());
        startActivityForResult(intent, FOR_ACH);
    }

    private void dismissDialog() {

        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }


    @Override
    public void onPaymentError(String message) {
        dismissDialog();
        presenter.logEvent(new ErrorEvent(message).getEvent(), ravePayInitializer.getPublicKey());
        Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode==RavePayActivity.RESULT_SUCCESS){
            //just to be sure this fragment sent the receiving intent
            if(requestCode == FOR_ACH){
                presenter.requeryTx(ravePayInitializer.getPublicKey());
            }
        }else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void showProgressIndicator(boolean active) {

        try {
            if (getActivity() != null) {
                if (getActivity().isFinishing()) {
                    return;
                }
            }

            if (progressDialog == null) {
                progressDialog = new ProgressDialog(getActivity());
                progressDialog.setCanceledOnTouchOutside(false);
                progressDialog.setMessage("Please wait...");
            }

            if (active && !progressDialog.isShowing()) {
                progressDialog.show();
            } else {
                progressDialog.dismiss();
            }
        }
        catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPaymentSuccessful(String status, String flwRef, String responseAsJSONString) {
        dismissDialog();

        Intent intent = new Intent();
        intent.putExtra("response", responseAsJSONString);
        if (getActivity() != null) {
            ((RavePayActivity) getActivity()).setRavePayResult(RavePayActivity.RESULT_SUCCESS, intent);
            getActivity().finish();
        }
    }

    @Override
    public void onPaymentFailed(String message, String responseAsJSONString) {
        dismissDialog();

        Intent intent = new Intent();
        intent.putExtra("response", responseAsJSONString);
        if (getActivity() != null) {
            ((RavePayActivity) getActivity()).setRavePayResult(RavePayActivity.RESULT_ERROR, intent);
            getActivity().finish();
        }
    }

    @Override
    public void showAmountError(String msg) {

        if (msg == null) {
            amountTil.setErrorEnabled(false);
        }
        amountTil.setError(msg);
    }

    @Override
    public void onRequerySuccessful(RequeryResponse response, String responseAsJSONString, String flwRef) {
        presenter.verifyRequeryResponse(response, responseAsJSONString, ravePayInitializer, flwRef);
    }

    @Override
    public void onResume() {
        super.onResume();
        presenter.onAttachView(this);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (presenter != null) {
            presenter.onDetachView();
        }
    }

    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        int i = view.getId();

        String fieldName = "";

        if (i == R.id.rave_amountEt) {
            fieldName = "Amount";
        }

        if (hasFocus) {
            presenter.logEvent(new StartTypingEvent(fieldName).getEvent(), ravePayInitializer.getPublicKey());
        }
    }
}
