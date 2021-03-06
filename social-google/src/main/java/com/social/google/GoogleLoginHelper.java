package com.social.google;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.social.google.downloader.ImageDownloaderTask;
import com.social.google.downloader.ImageException;
import com.social.google.models.User;

/**
 * Created by CS02 on 11/9/2016.
 */

public class GoogleLoginHelper {
  private static final String TAG = "GoogleLoginHelper";
  private static int RC_SIGN_IN = 100;
  //google api client
  private GoogleApiClient mGoogleApiClient;
  private Fragment fragment;
  private android.app.Fragment appFragment;
  private Activity activity;
  private GoogleLoginListener mGoogleLoginListener;
  private boolean isImageDownload;
  private GoogleApiClient.OnConnectionFailedListener failedListener =
      new GoogleApiClient.OnConnectionFailedListener() {
        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
          onLoginError(connectionResult.getErrorMessage());
        }
      };

  public GoogleLoginHelper(Fragment fragment) {
    this.fragment = fragment;
  }

  public GoogleLoginHelper(android.app.Fragment appFragment) {
    this.appFragment = appFragment;
  }

  public GoogleLoginHelper(Activity activity) {
    this.activity = activity;
  }

    /**
     * initialize google login helper this will not generate token. for that additionally pass client id
     * @param fragmentActivity activity context
     * @param listener GoogleLoginListener
     */
  public void init(FragmentActivity fragmentActivity, GoogleLoginListener listener) {
        init(fragmentActivity, listener, null);
  }

    /**
     * initialize google login helper this will generate token.
     * @param fragmentActivity activity context
     * @param listener GoogleLoginListener
     */
  public void init(FragmentActivity fragmentActivity, GoogleLoginListener listener, String googleClientId) {
    mGoogleLoginListener = listener;
    if (fragmentActivity == null) {
      onLoginError("Context is null");
      return;
    }
    Context context = getCurrentContext();
    if (context == null) {
      onLoginError("Context is null");
      return;
    }
      GoogleSignInOptions.Builder builder =
              new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                      .requestEmail();
      if (googleClientId != null) {
          builder.requestIdToken(googleClientId);
      }
      GoogleSignInOptions gso = builder.build();
    //Initializing google api client
    mGoogleApiClient =
        new GoogleApiClient.Builder(context).enableAutoManage(fragmentActivity, failedListener)
            .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
            .build();
  }

  private Context getCurrentContext() {
    if (activity != null) {
      return activity;
    }
    if (appFragment != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        return appFragment.getContext();
      } else {
        return appFragment.getActivity();
      }
    }
    if (fragment != null) {
      return fragment.getContext();
    }
    return null;
  }

    public GoogleApiClient getGoogleApiClient() {
        return mGoogleApiClient;
    }

    public void onDestroy(){
      if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
          Context context = getCurrentContext();
          if (context != null && context instanceof FragmentActivity) {
              mGoogleApiClient.stopAutoManage((FragmentActivity) context);
              mGoogleApiClient.disconnect();
          }
      }
  }

  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == RC_SIGN_IN) {
      GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
      handleSignInResult(result);
    }
  }

  public void loginWithGoogle(boolean isImageDownload) {
    this.isImageDownload = isImageDownload;
    if (mGoogleApiClient == null) {
      onLoginError("Google Api Client is null");
      return;
    }
    if (activity != null) {
      Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
      activity.startActivityForResult(signInIntent, RC_SIGN_IN);
    } else if (appFragment != null) {
      Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
      appFragment.startActivityForResult(signInIntent, RC_SIGN_IN);
    } else if (fragment != null) {
      Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
      fragment.startActivityForResult(signInIntent, RC_SIGN_IN);
    } else {
      onLoginError("Context is null");
    }
  }

  private void handleSignInResult(GoogleSignInResult result) {
    //If the login succeed
    if (result.isSuccess()) {
      //Getting google account
      GoogleSignInAccount acct = result.getSignInAccount();
      if (acct != null) {
        Log.d(TAG, "handleSignInResult: " + acct.getDisplayName());
        Log.d(TAG, "handleSignInResult: " + acct.getId());
        if (mGoogleLoginListener != null) {
          String profileImage = acct.getPhotoUrl() == null ? null : acct.getPhotoUrl().toString();
          User user = new User();
          user.setId(acct.getId());
          user.setEmail(acct.getEmail());
          user.setName(acct.getDisplayName());
          user.setProfileImage(profileImage);
            if (!TextUtils.isEmpty(profileImage) && isImageDownload) {
                downloadImage(user, acct.getIdToken());
            } else {
                onLoginSuccess(user, acct.getIdToken());
            }
        }
      } else {
        onLoginError("Login Failed");
      }
    } else {
      onLoginError("Login Failed");
    }
  }

  private void downloadImage(final User user, final String token) {
    final String imageUrl = user.getProfileImage();
    new ImageDownloaderTask(imageUrl, activity.getCacheDir().getAbsolutePath(), user.getId()) {
      @Override
      protected void onPostExecute(Exception e) {
        super.onPostExecute(e);
        if (e == null) {
          user.setProfileImage(imageUrl);
          user.setProfileImageLocal(getFinalImagePath());
          onLoginSuccess(user, token);
        } else {
          if (e instanceof ImageException) {
            onLoginError(e.getMessage());
          } else {
            onLoginError();
          }
        }
      }
    }.execute();
  }
  private void onLoginSuccess(User user, String token) {
    if (mGoogleLoginListener != null) {
      mGoogleLoginListener.onLogin(user, token);
    }
  }
  private void onLoginError(String error) {
    if (mGoogleLoginListener != null) {
      mGoogleLoginListener.onError(error);
    }
  }

  private void onLoginError() {
    onLoginError("Please try again");
  }

}
