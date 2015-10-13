package org.ohmage.mobility;

import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.android.gms.maps.SupportMapFragment;

import io.smalldatalab.omhclient.DSUAuth;
import io.smalldatalab.omhclient.DSUDataPoint;


/**
 * A fragment representing a list of Location points.
 */
public class VisualizeFragment extends Fragment {

    private WebView webview;

    public static VisualizeFragment newInstance() {
        VisualizeFragment fragment = new VisualizeFragment();
        return fragment;
    }

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
        });
        new AuthAndLoadUrlTask().execute();

        return view;
    }

    private class AuthAndLoadUrlTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... params) {
            String token = "";
            try {
                AccountManager accountManager = (AccountManager) getActivity().getSystemService(Context.ACCOUNT_SERVICE);

                token = accountManager.blockingGetAuthToken(DSUAuth.getDefaultAccount(getActivity()), DSUAuth.ACCESS_TOKEN_TYPE, true);
            } catch (Exception ex){
                ex.printStackTrace();
            }
            return token;
        }

        @Override
        protected void onPostExecute(String token) {
            webview.loadUrl("http://ohmage-omh.smalldata.io/mobility-ui/#access_token=" + token + "&token_type=bearer&scope=read_data_points");
        }
    }

}
