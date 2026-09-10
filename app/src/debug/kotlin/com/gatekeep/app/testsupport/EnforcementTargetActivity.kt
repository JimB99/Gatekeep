package com.gatekeep.app.testsupport

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class EnforcementTargetActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                text = "Gatekeep Test Target A"
                textSize = 24f
            },
        )
    }
}
