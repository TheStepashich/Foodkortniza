plugins {
    id("com.android.application")
}

android {
    namespace = "net.potatom.foodkortniza"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.potatom.foodkortniza"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Каталог магазинов публикуется на сайте Фудкортницы.
        buildConfigField("String", "STORES_URL", "\"https://foodkortniza.potatom.net/stores.json\"")
        // Только русская локаль: зависимости не тащат лишние переводы.
        androidResources.localeFilters.add("ru")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Пока распространяем сами: подписываем тем же debug-ключом, чтобы APK ставился так же.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    testImplementation("junit:junit:4.13.2")
}
