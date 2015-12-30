package org.ohmage.mobility;

import android.accounts.AccountManager;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import io.smalldatalab.omhclient.DSUAuth;


/**
 * A fragment representing a list of Location points.
 */
public class VisualizeFragment extends Fragment {

    private WebView webview;
    private ProgressBar progressBar;
    private View loadFailedView;

    // flag of loading status. it will be replaced with "false" if webclient received an error.
    private boolean loadingSucceeded = true;
    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public VisualizeFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.visualize_fragment, container, false);

        webview = (WebView) view.findViewById(R.id.webview);
        webview.setInitialScale(75);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.setWebViewClient(new WebViewClient() {
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return false; // then it is not handled by default action
            }

            public void onPageFinished(WebView view, String url) {
                if (loadingSucceeded) {
                    webview.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.GONE);
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Toast.makeText(VisualizeFragment.this.getContext(), "Your Internet connection may not be active Or " + description, Toast.LENGTH_LONG).show();
                loadFailedView.setVisibility(View.VISIBLE);
                webview.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                loadingSucceeded = false;
            }

            @TargetApi(android.os.Build.VERSION_CODES.M)
            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError rerr) {
                // Redirect to deprecated method, so you can use it in all SDK versions
                onReceivedError(view, rerr.getErrorCode(), rerr.getDescription().toString(), req.getUrl().toString());
            }

        });
        progressBar = (ProgressBar) view.findViewById(R.id.progressBar);
        loadFailedView = (View) view.findViewById(R.id.loadFailedView);
        Button reloadButton = (Button) view.findViewById(R.id.reloadButton);

        reloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStart();

            }
        });
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        loadingSucceeded = true;
        webview.setVisibility(View.GONE);
        loadFailedView.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        new AuthAndLoadUrlTask().execute();

    }

    private class AuthAndLoadUrlTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... params) {
            String token = null;
            try {
                AccountManager accountManager = (AccountManager) getActivity().getSystemService(Context.ACCOUNT_SERVICE);
                token = accountManager.blockingGetAuthToken(DSUAuth.getDefaultAccount(getActivity()), DSUAuth.ACCESS_TOKEN_TYPE, true);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return token;
        }
        @Override
        protected void onPostExecute(String token) {
            if (token != null) {
                webview.loadUrl("http://ohmage-omh.smalldata.io/mobility-ui/#access_token=" + token);
            }
        }
    }

}
