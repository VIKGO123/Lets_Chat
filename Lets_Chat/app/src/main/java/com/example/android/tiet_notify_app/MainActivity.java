package com.example.android.tiet_notify_app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    public static final int RC_SIGN_IN=1;
    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;
    private String message;

    private String mUsername;
    private FirebaseDatabase data;
    private DatabaseReference messageref;
    private ChildEventListener child;
    private FirebaseAuth auth;
    private FirebaseAuth.AuthStateListener authStateListener;
    private static final int RC_PHOTO_PICKER =  2;
    private FirebaseStorage storage;
    private StorageReference storageReference;
    private FirebaseRemoteConfig remoteConfig;
    public static final String Friendly_msg_length="Friendly_msg_length";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;

        //Declaring auth and database objects
        data=FirebaseDatabase.getInstance();
        auth=FirebaseAuth.getInstance();

        messageref=data.getReference().child("messages");
        storage=FirebaseStorage.getInstance();
        storageReference=storage.getReference().child("Photos");
        remoteConfig=FirebaseRemoteConfig.getInstance();


        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);


        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
                // TODO: Fire an intent to show an image picker
            }
        });


        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FriendlyMessage friendlyMessage=new FriendlyMessage(mMessageEditText.getText().toString(),mUsername,null);
                messageref.push().setValue(friendlyMessage);

                // TODO: Send messages on click

                // Clear input box
                mMessageEditText.setText("");
            }
        });



        authStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user=firebaseAuth.getCurrentUser();
                if(user!=null)
                {
                    OnSignedInInitialize(user.getDisplayName());
                    //user is signed in
                }
                else
                {
                    //else is signed out
                    OnSingedOutCleanUp();
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setAvailableProviders(Arrays.asList(
                                            new AuthUI.IdpConfig.GoogleBuilder().build(),
                                            new AuthUI.IdpConfig.EmailBuilder().build()))
                                    .build(),
                            RC_SIGN_IN);
                }

            }
        };


        FirebaseRemoteConfigSettings configSettings=new FirebaseRemoteConfigSettings.Builder().setDeveloperModeEnabled(BuildConfig.DEBUG).build();
        remoteConfig.setConfigSettings(configSettings);
        Map<String,Object> defaultconfig=new HashMap<>();
        defaultconfig.put(Friendly_msg_length,DEFAULT_MSG_LENGTH_LIMIT);
        remoteConfig.setDefaults(defaultconfig);
        fetchConfig();

//        if(getIntent().getExtras()!=null)
//        {
//            for(String key:getIntent().getExtras().keySet())
//            {
//                if(key.equals("title"))
//                {
//                    mUsername=getIntent().getExtras().getString(key);
//                }
//                else if(key.equals("message"))
//                {
//                   message =getIntent().getExtras().getString(key);
//                }
//                FriendlyMessage friendlyMessage=new FriendlyMessage(message,mUsername,null);
//                attachDatabase();
//            }
//        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==RC_SIGN_IN)
        {
            if(resultCode==RESULT_OK)
            {
                Toast.makeText(this,"You are Signed in",Toast.LENGTH_SHORT).show();

            }
            else if (resultCode==RESULT_CANCELED)
            {Toast.makeText(this,"Signed Out",Toast.LENGTH_SHORT).show();
                finish();
            }}
            else if (requestCode==RC_PHOTO_PICKER && resultCode==RESULT_OK)
            {   //displaying and storing photos
                Uri imageuri=data.getData();
                StorageReference photosref=storageReference.child(imageuri.getLastPathSegment());
                //STORES FILE TO CLOUD AND GET REF TO PUT IT ON THE WINDOW
                photosref.putFile(imageuri).addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        Uri downloadimage=taskSnapshot.getDownloadUrl();
                        FriendlyMessage friendlyMessage=new FriendlyMessage(null,mUsername,downloadimage.toString());
                        messageref.push().setValue(friendlyMessage);//setting it to database
                        attachDatabase();

                    }
                });


            }
        }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId()==R.id.sign_out_menu)
        {   AuthUI.getInstance().signOut(this);
            return true;
        }
        else
        {return super.onOptionsItemSelected(item);}
    }

    @Override
    protected void onResume() {
        super.onResume();
        auth.addAuthStateListener(authStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        auth.removeAuthStateListener(authStateListener);
        detachDatabase();
        mMessageAdapter.clear();
    }

    private void OnSignedInInitialize(String name)
    {
        mUsername=name;
        attachDatabase();



    }
    private void OnSingedOutCleanUp()
    {
    mUsername=ANONYMOUS;
    mMessageAdapter.clear();
    }
    private void attachDatabase()

    {
        if(child==null) {
            child = new ChildEventListener() {

                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                    mMessageAdapter.add(friendlyMessage);
                }

                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {


                }

                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {

                }

                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            };
            messageref.addChildEventListener(child);//triggered when some messages changes
        }
    }
    private void detachDatabase()
    {   if (child!=null) {
        messageref.removeEventListener(child);
        child=null;
    }

    }
    public void fetchConfig()
    {
        long cacheexpiration=3600;
        if(remoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled())
        {
            cacheexpiration=0;
        }
        remoteConfig.fetch(cacheexpiration).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                remoteConfig.activateFetched();
                applyRetrievedLengthLimit();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.w(TAG,"Error fetching Congif",e);
                applyRetrievedLengthLimit();

            }
        });
    }
    public void applyRetrievedLengthLimit()
    {
        Long friendly_msg_length=remoteConfig.getLong(Friendly_msg_length);
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(friendly_msg_length.intValue())});
        Log.d(TAG,"Friendly_message_length="+friendly_msg_length);
    }
}
