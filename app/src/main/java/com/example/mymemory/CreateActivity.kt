package com.example.mymemory

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mymemory.models.BoardSize
import com.example.mymemory.utils.BitmapScaler
import com.example.mymemory.utils.EXTRA_BOARD_SIZE
import com.example.mymemory.utils.isPermissionGranted
import com.example.mymemory.utils.requestPermission
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import java.io.ByteArrayOutputStream

class CreateActivity : AppCompatActivity() {

    companion object{
        private const val TAG = "CreateActivity"
        private const val PICK_PHOTO_CODE = 655
        private const val READ_EXTERNAL_PHOTOS_CODE = 248
        private const val READ_PHOTOS_PERMISSION = android.Manifest.permission.READ_EXTERNAL_STORAGE
        private const val MIN_GAME_NAME_LENGTH = 3
        private const val MAX_GAME_NAME_LENGTH = 14
    }

    private lateinit var rvImagePicker: RecyclerView
    private lateinit var etGameName: EditText
    private lateinit var btnSave: Button
    private lateinit var adapter: ImagePickerAdapter

    private lateinit var boardSize: BoardSize
    private var numImagesRequired = -1
    private val chosenImageUris = mutableListOf<Uri>()

    private val storage = Firebase.storage
    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)

        rvImagePicker = findViewById(R.id.rvImagePicker)
        etGameName = findViewById(R.id.etGameName)
        btnSave = findViewById(R.id.btnSave)

        btnSave.setOnClickListener{
            saveDataToFirebase()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        boardSize = intent.getSerializableExtra(EXTRA_BOARD_SIZE) as BoardSize
        numImagesRequired = boardSize.getNumPairs()
        supportActionBar?.title = " Choose your images (0 / $numImagesRequired)"

        etGameName.filters = arrayOf(InputFilter.LengthFilter(MAX_GAME_NAME_LENGTH))
        etGameName.addTextChangedListener( object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: Editable?) {
                btnSave.isEnabled = shouldEnableSaveButton()
            }
        })
        adapter = ImagePickerAdapter(this, chosenImageUris, boardSize, object: ImagePickerAdapter.ImageClickListener{
            override fun onPlaceHolderClicked() {
                // launch photo selection intent
                if (isPermissionGranted(this@CreateActivity, READ_PHOTOS_PERMISSION)){
                launchIntentForPhotos()

            } else{
                requestPermission(this@CreateActivity, READ_PHOTOS_PERMISSION, READ_EXTERNAL_PHOTOS_CODE)
                }
            }

        })
        rvImagePicker.adapter =adapter
        rvImagePicker.setHasFixedSize(true)
        rvImagePicker.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {

        if (requestCode == READ_EXTERNAL_PHOTOS_CODE){
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                launchIntentForPhotos()
            }
            else
            {
                Toast.makeText(this, "In order to create a custom game, you need to provide access to your photos", Toast.LENGTH_LONG).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home){
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_PHOTO_CODE || resultCode != Activity.RESULT_OK || data == null){
            Log.w(TAG, "Did not get data back from the launched activity, user cancelled the selection process")
            return
        }
        val selectedUri: Uri? = data.data
        val clipData: ClipData? = data.clipData
        if(clipData != null){
            Log.i(TAG, "clipData numImages ${clipData.itemCount}: $clipData")
            for (i: Int in 0 until clipData.itemCount){
                val clipItem: ClipData.Item = clipData.getItemAt(i)
                if(chosenImageUris.size < numImagesRequired){
                    chosenImageUris.add(clipItem.uri)
                }
            }
        }
        else if (selectedUri != null){
            Log.i(TAG, "data: $selectedUri")
            chosenImageUris.add(selectedUri)
        }
        adapter.notifyDataSetChanged()
        supportActionBar?.title = "Choose pics ${chosenImageUris.size}/ $numImagesRequired"
        btnSave.isEnabled = shouldEnableSaveButton()
    }

    private fun saveDataToFirebase() {
        val customGameName = etGameName.text.toString()
        var didEncounterError = false
        val uploadedImageUrl = mutableListOf<String>()


        Log.i(TAG, "save Data to Firebase")
        for ((index: Int, photoUri: Uri) in chosenImageUris.withIndex()){
            val imageByteArray = getImageByteArray(photoUri)
            val filePath = "images/$customGameName/${System.currentTimeMillis()}- ${index}.jpg"
            val photoReference: StorageReference = storage.reference.child(filePath)
            photoReference.putBytes(imageByteArray)
                    .continueWithTask{photoUploadTask ->
                        Log.i(TAG, "Uploaded Bytes: ${photoUploadTask.result?.bytesTransferred}")
                        photoReference.downloadUrl

                    }.addOnCompleteListener{ downloadUrlTask ->
                        if (!downloadUrlTask.isSuccessful){
                            Log.e(TAG, "Exception with Firebase storage", downloadUrlTask.exception)
                            Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show()
                            didEncounterError = true
                            return@addOnCompleteListener
                        }
                        if (didEncounterError){
                            return@addOnCompleteListener
                        }
                        val downloadUrl: String = downloadUrlTask.result.toString()
                        uploadedImageUrl.add(downloadUrl)
                        Log.i(TAG, "Finished uploading $photoUri, num uploaded ${uploadedImageUrl.size}")
                        if (uploadedImageUrl.size == chosenImageUris.size){
                            handleAllImagesUploaded(customGameName, uploadedImageUrl)
                        }
                    }
        }
    }

    private fun handleAllImagesUploaded(
            gameName: String,
            imageUrls: MutableList<String>) {
        // TODO: upload this info to firestore


    }

    private fun getImageByteArray(photoUri: Uri): ByteArray {

        val originalBitmap : Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
            val source : ImageDecoder.Source = ImageDecoder.createSource(contentResolver, photoUri)
            ImageDecoder.decodeBitmap(source)
        }else{
            MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
        }
        Log.i(TAG, "Original width ${originalBitmap.width} and height ${originalBitmap.height}")
        val scaledBitmap = BitmapScaler.scaleToFitHeight(originalBitmap, 250 )
        Log.i(TAG, "Scaled width ${scaledBitmap.height} and height ${scaledBitmap.height}")
        val byteOutputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteOutputStream)
        return byteOutputStream.toByteArray()
    }

    private fun shouldEnableSaveButton(): Boolean {
        // check if we should enable the save button or not
        if (chosenImageUris.size != numImagesRequired){
            return false
        }
        if(etGameName.text.isBlank() || etGameName.text.length < MIN_GAME_NAME_LENGTH){
            return false
        }
        return true

    }

    private fun launchIntentForPhotos() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        intent.putExtra(
                Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(Intent.createChooser(intent, "Choose Photos"), PICK_PHOTO_CODE)
    }
}