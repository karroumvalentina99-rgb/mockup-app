package org.legalbet.mockupupload

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.settings_title)

        val baseUrl = findViewById<TextInputEditText>(R.id.baseUrl)
        val token = findViewById<TextInputEditText>(R.id.token)
        val cfId = findViewById<TextInputEditText>(R.id.cfId)
        val cfSecret = findViewById<TextInputEditText>(R.id.cfSecret)
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)

        baseUrl.setText(Prefs.baseUrl(this))
        token.setText(Prefs.token(this))
        cfId.setText(Prefs.cfId(this))
        cfSecret.setText(Prefs.cfSecret(this))

        btnSave.setOnClickListener {
            Prefs.save(
                this,
                baseUrl.text?.toString().orEmpty(),
                token.text?.toString().orEmpty(),
                cfId.text?.toString().orEmpty(),
                cfSecret.text?.toString().orEmpty()
            )
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
