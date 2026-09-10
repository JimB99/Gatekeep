package com.gatekeep.app.testsupport

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class EnforcementTargetActivityB : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                text = "Gatekeep Test Target B"
                textSize = 24f
            },
        )
    }
}
