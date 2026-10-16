package com.gatekeep.app.support

/**
 * Ship-bar emulator gate. Default `:app:connectedDebugAndroidTest` runs only these
 * methods (under 10 minutes). Rule matrices stay on JVM. Overlay combinatorics
 * (`@LargeTest`) are opt-in: `-Pgatekeep.e2eGate=false`.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class E2eGate
