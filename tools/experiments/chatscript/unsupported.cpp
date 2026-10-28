// Keep the universal APK installable on 32-bit devices. Kotlin checks process
// bitness and never launches this placeholder; all dialogue stays in Kotlin.
int main() { return 2; }
